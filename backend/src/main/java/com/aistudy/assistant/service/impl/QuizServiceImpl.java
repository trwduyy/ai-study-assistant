package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.dto.quiz.AttemptDetailResponse;
import com.aistudy.assistant.dto.quiz.GenerateQuizRequest;
import com.aistudy.assistant.dto.quiz.QuestionAnswerSubmit;
import com.aistudy.assistant.dto.quiz.QuizAttemptHistoryResponse;
import com.aistudy.assistant.dto.quiz.QuizExamResponse;
import com.aistudy.assistant.dto.quiz.QuizResponse;
import com.aistudy.assistant.dto.quiz.QuizSubmitResultResponse;
import com.aistudy.assistant.dto.quiz.SubmitQuizRequest;
import com.aistudy.assistant.entity.AttemptAnswer;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.entity.Difficulty;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.entity.OptionLetter;
import com.aistudy.assistant.entity.Quiz;
import com.aistudy.assistant.entity.QuizAttempt;
import com.aistudy.assistant.entity.QuizQuestion;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.repository.QuizAttemptRepository;
import com.aistudy.assistant.repository.QuizQuestionRepository;
import com.aistudy.assistant.repository.QuizRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.service.GeminiClientService;
import com.aistudy.assistant.service.QuizService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final CourseRepository courseRepository;
    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final UserRepository userRepository;
    private final GeminiClientService geminiClientService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public QuizResponse generateQuiz(GenerateQuizRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Course course = courseRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getCourseId(), currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", request.getCourseId()));

        Material material = null;
        if (request.getMaterialId() != null) {
            material = materialRepository.findByIdAndOwnerIdAndDeletedAtIsNull(request.getMaterialId(), currentUserId)
                    .orElseThrow(() -> new ResourceNotFoundException("Material", "id", request.getMaterialId()));
        }

        String sourceText = resolveSourceText(course, material);
        if (!StringUtils.hasText(sourceText)) {
            throw new BadRequestException("Cannot generate quiz: No study material with successfully extracted text found.");
        }

        log.info("Generating quiz '{}' with {} questions (difficulty: {}) for course: {}",
                request.getTitle(), request.getQuestionCount(), request.getDifficulty(), course.getId());

        List<GeneratedQuestion> generatedQuestions = callAiToGenerateQuestions(
                sourceText,
                request.getTitle(),
                request.getDifficulty(),
                request.getQuestionCount()
        );

        if (generatedQuestions.isEmpty()) {
            throw new BadRequestException("AI could not generate quiz questions from the provided material.");
        }

        Quiz quiz = Quiz.builder()
                .user(currentUser)
                .course(course)
                .material(material)
                .title(request.getTitle().trim())
                .difficulty(request.getDifficulty())
                .questionCount(generatedQuestions.size())
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> questionsToSave = new ArrayList<>();
        for (int i = 0; i < generatedQuestions.size(); i++) {
            GeneratedQuestion gq = generatedQuestions.get(i);
            QuizQuestion qq = QuizQuestion.builder()
                    .quiz(savedQuiz)
                    .questionText(gq.questionText())
                    .optionA(gq.optionA())
                    .optionB(gq.optionB())
                    .optionC(gq.optionC())
                    .optionD(gq.optionD())
                    .correctOption(gq.correctOption())
                    .explanation(gq.explanation())
                    .orderIndex(i + 1)
                    .build();
            questionsToSave.add(qq);
        }

        quizQuestionRepository.saveAll(questionsToSave);
        savedQuiz.setQuestions(questionsToSave);

        log.info("Quiz {} successfully generated with {} questions", savedQuiz.getId(), questionsToSave.size());
        return QuizResponse.fromEntity(savedQuiz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizResponse> getQuizzesByCourse(Long courseId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!courseRepository.existsByIdAndOwnerIdAndDeletedAtIsNull(courseId, currentUserId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }

        List<Quiz> quizzes = quizRepository.findByCourseIdAndUserIdOrderByCreatedAtDesc(courseId, currentUserId);
        return quizzes.stream()
                .map(QuizResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizExamResponse getQuizExamDetail(Long quizId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Quiz quiz = quizRepository.findWithQuestionsByIdAndUserId(quizId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        // Security requirement: Ensure correctOption and explanation are NEVER leaked in exam mode
        return QuizExamResponse.fromEntity(quiz);
    }

    @Override
    @Transactional
    public QuizSubmitResultResponse submitQuiz(Long quizId, SubmitQuizRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Quiz quiz = quizRepository.findWithQuestionsByIdAndUserId(quizId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));
        User currentUser = quiz.getUser();

        List<QuizQuestion> questions = quiz.getQuestions();
        if (questions == null || questions.isEmpty()) {
            questions = quizQuestionRepository.findByQuizIdOrderByOrderIndexAscIdAsc(quizId);
        }

        int totalQuestions = questions.size();
        if (totalQuestions == 0) {
            throw new BadRequestException("This quiz has no questions to evaluate");
        }

        Map<Long, OptionLetter> userChoiceMap = new HashMap<>();
        if (request.getAnswers() != null) {
            for (QuestionAnswerSubmit item : request.getAnswers()) {
                if (item.getQuestionId() != null) {
                    userChoiceMap.put(item.getQuestionId(), item.getSelectedOption());
                }
            }
        }

        int correctCount = 0;
        List<AttemptAnswer> attemptAnswers = new ArrayList<>();

        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .user(currentUser)
                .totalQuestions(totalQuestions)
                .startedAt(Instant.now())
                .submittedAt(Instant.now())
                .build();

        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        for (QuizQuestion q : questions) {
            OptionLetter selectedOption = userChoiceMap.get(q.getId());
            boolean isCorrect = selectedOption != null && selectedOption == q.getCorrectOption();
            if (isCorrect) {
                correctCount++;
            }

            AttemptAnswer ans = AttemptAnswer.builder()
                    .attempt(savedAttempt)
                    .question(q)
                    .selectedOption(selectedOption)
                    .isCorrect(isCorrect)
                    .build();
            attemptAnswers.add(ans);
        }

        attemptAnswerRepository.saveAll(attemptAnswers);

        // Calculate score on a 100-point scale
        BigDecimal score = BigDecimal.valueOf((correctCount * 100.0) / totalQuestions)
                .setScale(2, RoundingMode.HALF_UP);

        savedAttempt.setScore(score);
        savedAttempt.setCorrectCount(correctCount);
        savedAttempt.setAnswers(attemptAnswers);
        quizAttemptRepository.save(savedAttempt);

        log.info("Quiz {} submitted by user {}: score={}, correct={}/{}",
                quizId, currentUserId, score, correctCount, totalQuestions);

        return QuizSubmitResultResponse.builder()
                .attemptId(savedAttempt.getId())
                .quizId(quizId)
                .score(score)
                .totalQuestions(totalQuestions)
                .correctCount(correctCount)
                .submittedAt(savedAttempt.getSubmittedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptDetailResponse getAttemptDetail(Long attemptId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        QuizAttempt attempt = quizAttemptRepository.findWithDetailsByIdAndUserId(attemptId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizAttempt", "id", attemptId));

        return AttemptDetailResponse.fromEntity(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizAttemptHistoryResponse> getQuizAttempts(Long quizId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        findQuizAndValidateOwnership(quizId, currentUserId);

        List<QuizAttempt> attempts = quizAttemptRepository.findByQuizIdAndUserIdOrderByStartedAtDesc(quizId, currentUserId);
        return attempts.stream()
                .map(QuizAttemptHistoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    private String resolveSourceText(Course course, Material material) {
        if (material != null) {
            String text = loadMaterialText(material.getId());
            if (material.getStatus() == MaterialStatus.EXTRACTED && StringUtils.hasText(text)) {
                return text;
            }
            throw new BadRequestException("Selected material has not been successfully extracted or is empty.");
        }

        List<Material> materials = materialRepository.findByCourseIdAndOwnerIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                course.getId(), course.getOwner().getId()
        );

        StringBuilder sb = new StringBuilder();
        for (Material m : materials) {
            String text = loadMaterialText(m.getId());
            if (m.getStatus() == MaterialStatus.EXTRACTED && StringUtils.hasText(text)) {
                sb.append("\n--- ").append(m.getFileName()).append(" ---\n");
                sb.append(text);
                if (sb.length() > 25000) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    private String loadMaterialText(Long materialId) {
        return materialChunkRepository.findByMaterialIdOrderByChunkIndexAsc(materialId).stream()
                .map(MaterialChunk::getContent)
                .collect(Collectors.joining("\n\n"));
    }

    private List<GeneratedQuestion> callAiToGenerateQuestions(
            String sourceText,
            String title,
            Difficulty difficulty,
            int questionCount) {

        String systemPrompt = """
                Bạn là chuyên gia giáo dục và khảo thí trắc nghiệm chuẩn mực quốc tế.
                Nhiệm vụ của bạn là đọc tài liệu học tập và tạo ra bộ câu hỏi trắc nghiệm 4 lựa chọn (A, B, C, D) chất lượng cao bằng tiếng Việt.
                Quy tắc nghiêm ngặt:
                1. Mỗi câu hỏi phải có đúng 4 phương án: optionA, optionB, optionC, optionD.
                2. correctOption BẮT BUỘC phải là đúng 1 trong 4 ký tự viết hoa: "A", "B", "C", hoặc "D".
                3. explanation phải giải thích cặn kẽ vì sao đáp án đó là đúng và trích dẫn/suy luận từ tài liệu.
                4. Phải bám sát mức độ khó được yêu cầu và câu hỏi không được trùng lặp.
                """;

        String userPrompt = String.format("""
                Hãy tạo đúng %d câu hỏi trắc nghiệm khách quan mức độ '%s' cho chủ đề/bài học: '%s'.

                --- TÀI LIỆU HỌC TẬP ---
                %s
                --- HẾT TÀI LIỆU ---

                Bắt buộc trả về đúng định dạng JSON Array chứa các object có các trường:
                - questionText (string)
                - optionA (string)
                - optionB (string)
                - optionC (string)
                - optionD (string)
                - correctOption (string: "A", "B", "C", hoặc "D")
                - explanation (string)
                """, questionCount, difficulty.name(), title,
                sourceText.length() > 20000 ? sourceText.substring(0, 20000) : sourceText);

        Map<String, Object> schema = buildQuizJsonSchema();
        String jsonResult = geminiClientService.generateStructuredJson(systemPrompt, userPrompt, schema);

        return parseAndValidateQuestions(jsonResult);
    }

    private Map<String, Object> buildQuizJsonSchema() {
        Map<String, Object> questionProperties = new HashMap<>();
        questionProperties.put("questionText", Map.of("type", "STRING"));
        questionProperties.put("optionA", Map.of("type", "STRING"));
        questionProperties.put("optionB", Map.of("type", "STRING"));
        questionProperties.put("optionC", Map.of("type", "STRING"));
        questionProperties.put("optionD", Map.of("type", "STRING"));
        questionProperties.put("correctOption", Map.of("type", "STRING", "enum", List.of("A", "B", "C", "D")));
        questionProperties.put("explanation", Map.of("type", "STRING"));

        Map<String, Object> itemObject = new HashMap<>();
        itemObject.put("type", "OBJECT");
        itemObject.put("properties", questionProperties);
        itemObject.put("required", List.of("questionText", "optionA", "optionB", "optionC", "optionD", "correctOption", "explanation"));

        Map<String, Object> arraySchema = new HashMap<>();
        arraySchema.put("type", "ARRAY");
        arraySchema.put("items", itemObject);

        return arraySchema;
    }

    private List<GeneratedQuestion> parseAndValidateQuestions(String json) {
        List<GeneratedQuestion> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                if (root.has("questions") && root.get("questions").isArray()) {
                    root = root.get("questions");
                } else {
                    log.warn("Expected JSON Array for quiz questions but got: {}", json);
                    return list;
                }
            }

            for (JsonNode node : root) {
                String questionText = node.path("questionText").asText("");
                String optionA = node.path("optionA").asText("");
                String optionB = node.path("optionB").asText("");
                String optionC = node.path("optionC").asText("");
                String optionD = node.path("optionD").asText("");
                String correctOptStr = node.path("correctOption").asText("").trim().toUpperCase(Locale.ROOT);
                String explanation = node.path("explanation").asText("");

                if (!StringUtils.hasText(questionText) || !StringUtils.hasText(optionA) || !StringUtils.hasText(optionB)) {
                    continue;
                }

                OptionLetter correctOption;
                try {
                    correctOption = OptionLetter.valueOf(correctOptStr);
                } catch (IllegalArgumentException ex) {
                    correctOption = OptionLetter.A;
                }

                list.add(new GeneratedQuestion(
                        questionText.trim(),
                        optionA.trim(),
                        optionB.trim(),
                        StringUtils.hasText(optionC) ? optionC.trim() : "None of the above",
                        StringUtils.hasText(optionD) ? optionD.trim() : "All of the above",
                        correctOption,
                        explanation.trim()
                ));
            }
        } catch (Exception ex) {
            log.error("Failed to parse AI quiz JSON: {}", ex.getMessage(), ex);
            throw new BadRequestException("AI returned invalid JSON format for quiz questions: " + ex.getMessage());
        }

        return list;
    }

    private Quiz findQuizAndValidateOwnership(Long quizId, Long userId) {
        return quizRepository.findByIdAndUserId(quizId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));
    }

    public record GeneratedQuestion(
            String questionText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            OptionLetter correctOption,
            String explanation
    ) {}
}
