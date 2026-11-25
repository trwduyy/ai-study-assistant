package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.idempotency.IdempotencyService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.dto.AttemptAnswerInput;
import com.aistudy.assistant.learning.dto.AttemptReviewResponse;
import com.aistudy.assistant.learning.dto.QuizAttemptSummaryResponse;
import com.aistudy.assistant.learning.dto.QuizDetailResponse;
import com.aistudy.assistant.learning.dto.QuizSubmitResultResponse;
import com.aistudy.assistant.learning.dto.QuizSummaryResponse;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftRequest;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftResponse;
import com.aistudy.assistant.learning.dto.StartAttemptResponse;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.dto.SubmitQuestionResultDto;
import com.aistudy.assistant.learning.dto.UpdateQuizRequest;
import com.aistudy.assistant.learning.entity.AttemptAnswer;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import com.aistudy.assistant.learning.mapper.QuizMapper;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.QuestionOptionRepository;
import com.aistudy.assistant.learning.repository.QuestionRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private static final BigDecimal MAX_SCORE = new BigDecimal("10.00");
    private static final Duration SUBMIT_IDEMPOTENCY_TTL = Duration.ofHours(24);

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final UserRepository userRepository;
    private final IdempotencyService idempotencyService;
    private final QuizMapper quizMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<QuizSummaryResponse> listQuizzes(Long courseId, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        return quizRepository.findProjectedByOwnerId(userId, courseId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizDetailResponse getQuizForAttempt(Long quizId) {
        Quiz quiz = requireOwnedQuiz(quizId);
        List<Question> questions = questionRepository.findByQuizIdWithDetails(quizId);
        Map<Long, List<QuestionOption>> optionsMap = loadOptionsMap(questions);
        return quizMapper.toTakeDetailResponse(quiz, questions, optionsMap);
    }

    @Override
    @Transactional
    public QuizSummaryResponse updateQuiz(Long quizId, UpdateQuizRequest request) {
        Quiz quiz = requireOwnedQuiz(quizId);
        if (!request.version().equals(quiz.getVersion())) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }
        quiz.setTitle(request.title().trim());
        quiz.setDifficulty(request.difficulty());
        quiz = quizRepository.saveAndFlush(quiz);
        return quizMapper.toSummaryResponse(quiz);
    }

    @Override
    @Transactional
    public void deleteQuiz(Long quizId) {
        Quiz quiz = requireOwnedQuiz(quizId);
        quiz.markDeleted();
        quizRepository.save(quiz);
    }

    @Override
    @Transactional
    public StartAttemptResponse startAttempt(Long quizId) {
        Long userId = SecurityUtils.currentUserId();
        Quiz quiz = requireOwnedQuiz(quizId);
        User user = userRepository.getReferenceById(userId);
        int totalQuestions = (int) questionRepository.countByQuizId(quizId);

        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .user(user)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now())
                .score(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .maxScore(MAX_SCORE)
                .correctCount(0)
                .totalQuestions(totalQuestions)
                .build();
        attempt = quizAttemptRepository.save(attempt);
        return quizMapper.toStartAttemptResponse(attempt);
    }

    @Override
    @Transactional
    public SaveAttemptDraftResponse saveAttemptDraft(
            Long quizId,
            Long attemptId,
            SaveAttemptDraftRequest request) {
        Long userId = SecurityUtils.currentUserId();
        QuizAttempt attempt = requireOwnedInProgressAttempt(quizId, attemptId, userId, request.version());

        List<Question> questions = questionRepository.findByQuizIdWithDetails(quizId);
        Map<Long, List<QuestionOption>> optionsMap = loadOptionsMap(questions);
        Map<Long, Set<Long>> allowedOptionsByQuestion = buildAllowedOptionIdMap(questions, optionsMap);

        Map<Long, AttemptAnswer> existingByQuestionId = attemptAnswerRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(aa -> aa.getQuestion().getId(), aa -> aa));
        Map<Long, Question> questionById = questions.stream()
                .collect(Collectors.toMap(Question::getId, q -> q));

        List<AttemptAnswer> toSave = new ArrayList<>();
        for (AttemptAnswerInput input : request.answers()) {
            Question question = questionById.get(input.questionId());
            if (question == null) {
                throw new AppException(ErrorCode.QUESTION_NOT_FOUND);
            }
            List<Long> normalizedIds = validateAndDeduplicateSelections(
                    input.selectedOptionIds(),
                    allowedOptionsByQuestion.getOrDefault(question.getId(), Set.of())
            );
            AttemptAnswer answer = existingByQuestionId.getOrDefault(
                    question.getId(),
                    AttemptAnswer.builder().attempt(attempt).question(question).isCorrect(false).build()
            );
            answer.setSelectedOptionIds(normalizedIds);
            toSave.add(answer);
        }
        attemptAnswerRepository.saveAll(toSave);

        Instant now = Instant.now();
        Instant nextUpdated = attempt.getUpdatedAt() != null && !now.isAfter(attempt.getUpdatedAt())
                ? attempt.getUpdatedAt().plusMillis(1)
                : now;
        attempt.setUpdatedAt(nextUpdated);
        attempt = quizAttemptRepository.saveAndFlush(attempt);

        return new SaveAttemptDraftResponse(
                attempt.getId(),
                attempt.getStatus(),
                attempt.getVersion(),
                attempt.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public QuizSubmitResultResponse submitAttempt(
            Long quizId,
            Long attemptId,
            String idempotencyKey,
            SubmitAttemptRequest request) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header Idempotency-Key là bắt buộc khi nộp bài.");
        }
        String normalizedKey = idempotencyKey.trim();
        if (normalizedKey.length() > 64) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không được vượt quá 64 ký tự.");
        }

        Long userId = SecurityUtils.currentUserId();
        String endpoint = "POST:/api/v1/quizzes/" + quizId + "/attempts/" + attemptId + "/submit";
        String requestHash = idempotencyService.hashPayload(request);

        Optional<QuizSubmitResultResponse> cached = idempotencyService.findCachedResponse(
                userId,
                normalizedKey,
                endpoint,
                requestHash,
                QuizSubmitResultResponse.class
        );
        if (cached.isPresent()) {
            return cached.get();
        }

        QuizAttempt attempt = requireOwnedInProgressAttempt(quizId, attemptId, userId, request.version());

        List<Question> questions = questionRepository.findByQuizIdWithDetails(quizId);
        Map<Long, List<QuestionOption>> optionsMap = loadOptionsMap(questions);
        Map<Long, Set<Long>> allowedOptionsByQuestion = buildAllowedOptionIdMap(questions, optionsMap);

        Map<Long, AttemptAnswer> existingDrafts = attemptAnswerRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(aa -> aa.getQuestion().getId(), aa -> aa));
        Map<Long, List<Long>> submittedByQuestion = new LinkedHashMap<>();
        existingDrafts.forEach((qId, ans) -> submittedByQuestion.put(
                qId,
                ans.getSelectedOptionIds() != null ? ans.getSelectedOptionIds() : List.of()
        ));

        if (request.answers() != null) {
            Map<Long, Question> questionById = questions.stream()
                    .collect(Collectors.toMap(Question::getId, q -> q));
            for (AttemptAnswerInput input : request.answers()) {
                if (!questionById.containsKey(input.questionId())) {
                    throw new AppException(ErrorCode.QUESTION_NOT_FOUND);
                }
                List<Long> cleaned = validateAndDeduplicateSelections(
                        input.selectedOptionIds(),
                        allowedOptionsByQuestion.getOrDefault(input.questionId(), Set.of())
                );
                submittedByQuestion.put(input.questionId(), cleaned);
            }
        }

        int correctCount = 0;
        int totalQuestions = questions.size();
        List<AttemptAnswer> answersToPersist = new ArrayList<>();
        List<SubmitQuestionResultDto> details = new ArrayList<>();

        for (Question question : questions) {
            List<QuestionOption> options = optionsMap.getOrDefault(question.getId(), List.of());
            List<Long> correctOptionIds = options.stream()
                    .filter(QuestionOption::isCorrectOption)
                    .map(QuestionOption::getId)
                    .toList();

            List<Long> selectedIds = submittedByQuestion.getOrDefault(question.getId(), List.of());
            Set<Long> selectedSet = new LinkedHashSet<>(selectedIds);
            Set<Long> correctSet = new LinkedHashSet<>(correctOptionIds);
            boolean isCorrect = !correctSet.isEmpty() && selectedSet.equals(correctSet);
            if (isCorrect) {
                correctCount++;
            }

            AttemptAnswer answer = existingDrafts.getOrDefault(
                    question.getId(),
                    AttemptAnswer.builder().attempt(attempt).question(question).build()
            );
            answer.setSelectedOptionIds(selectedIds);
            answer.setIsCorrect(isCorrect);
            answersToPersist.add(answer);

            details.add(new SubmitQuestionResultDto(
                    question.getId(),
                    selectedIds,
                    correctOptionIds,
                    isCorrect,
                    question.getExplanation()
            ));
        }

        attemptAnswerRepository.saveAll(answersToPersist);

        Instant submittedAt = Instant.now();
        int durationSeconds = request.durationSeconds() != null
                ? request.durationSeconds()
                : (int) Math.max(0L, Duration.between(attempt.getStartedAt(), submittedAt).getSeconds());

        BigDecimal score = totalQuestions == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : MAX_SCORE.multiply(BigDecimal.valueOf(correctCount))
                        .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(submittedAt);
        attempt.setDurationSeconds(durationSeconds);
        attempt.setCorrectCount(correctCount);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setScore(score);
        attempt.setMaxScore(MAX_SCORE);
        attempt.setIdempotencyKey(normalizedKey);
        attempt = quizAttemptRepository.saveAndFlush(attempt);

        QuizSubmitResultResponse response = new QuizSubmitResultResponse(
                attempt.getId(),
                attempt.getQuiz().getId(),
                attempt.getStatus(),
                attempt.getScore().doubleValue(),
                attempt.getMaxScore().doubleValue(),
                attempt.getCorrectCount(),
                attempt.getTotalQuestions(),
                attempt.getDurationSeconds(),
                attempt.getSubmittedAt(),
                details
        );

        idempotencyService.saveResponse(
                userId,
                normalizedKey,
                endpoint,
                requestHash,
                200,
                response,
                SUBMIT_IDEMPOTENCY_TTL
        );

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<QuizAttemptSummaryResponse> listAttempts(Long quizId, Pageable pageable) {
        Long userId = SecurityUtils.currentUserId();
        requireOwnedQuiz(quizId);
        return quizAttemptRepository.findProjectedByQuizIdAndUserId(quizId, userId, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public AttemptReviewResponse getAttemptReview(Long attemptId) {
        Long userId = SecurityUtils.currentUserId();
        QuizAttempt attempt = quizAttemptRepository.findWithQuizAndUserById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        if (!attempt.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (attempt.getQuiz().isDeleted()) {
            throw new AppException(ErrorCode.QUIZ_NOT_FOUND);
        }
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Vui lòng nộp bài trước khi xem chi tiết đáp án và giải thích.");
        }

        List<Question> questions = questionRepository.findByQuizIdWithDetails(attempt.getQuiz().getId());
        Map<Long, List<QuestionOption>> optionsMap = loadOptionsMap(questions);
        Map<Long, AttemptAnswer> answersMap = attemptAnswerRepository.findByAttemptId(attemptId).stream()
                .collect(Collectors.toMap(aa -> aa.getQuestion().getId(), aa -> aa));

        return quizMapper.toReviewResponse(attempt, questions, optionsMap, answersMap);
    }

    private Quiz requireOwnedQuiz(Long quizId) {
        Long userId = SecurityUtils.currentUserId();
        Quiz quiz = quizRepository.findByIdAndDeletedAtIsNull(quizId)
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_NOT_FOUND));
        if (!quiz.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return quiz;
    }

    private QuizAttempt requireOwnedInProgressAttempt(
            Long quizId,
            Long attemptId,
            Long userId,
            Integer expectedVersion) {
        QuizAttempt attempt = quizAttemptRepository.findWithQuizAndUserById(attemptId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        if (!attempt.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (!attempt.getQuiz().getId().equals(quizId) || attempt.getQuiz().isDeleted()) {
            throw new AppException(ErrorCode.ATTEMPT_NOT_FOUND);
        }
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            throw new AppException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        }
        if (attempt.getStatus() == AttemptStatus.EXPIRED
                || (attempt.getDeadlineAt() != null && Instant.now().isAfter(attempt.getDeadlineAt()))) {
            throw new AppException(ErrorCode.ATTEMPT_EXPIRED);
        }
        if (expectedVersion != null && !expectedVersion.equals(attempt.getVersion())) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }
        return attempt;
    }

    private Map<Long, List<QuestionOption>> loadOptionsMap(List<Question> questions) {
        if (questions.isEmpty()) {
            return Map.of();
        }
        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        return questionOptionRepository.findByQuestionIdInOrderByLabel(questionIds).stream()
                .collect(Collectors.groupingBy(opt -> opt.getQuestion().getId()));
    }

    private Map<Long, Set<Long>> buildAllowedOptionIdMap(
            List<Question> questions,
            Map<Long, List<QuestionOption>> optionsMap) {
        Map<Long, Set<Long>> allowedMap = new LinkedHashMap<>();
        for (Question question : questions) {
            Set<Long> optionIds = optionsMap.getOrDefault(question.getId(), List.of()).stream()
                    .map(QuestionOption::getId)
                    .collect(Collectors.toSet());
            allowedMap.put(question.getId(), optionIds);
        }
        return allowedMap;
    }

    private List<Long> validateAndDeduplicateSelections(List<Long> rawSelected, Set<Long> allowedOptionIds) {
        if (rawSelected == null || rawSelected.isEmpty()) {
            return List.of();
        }
        Set<Long> cleaned = new LinkedHashSet<>();
        for (Long optionId : rawSelected) {
            if (optionId == null || !allowedOptionIds.contains(optionId)) {
                throw new AppException(ErrorCode.BAD_REQUEST, "Lựa chọn đáp án không thuộc câu hỏi tương ứng.");
            }
            cleaned.add(optionId);
        }
        return new ArrayList<>(cleaned);
    }
}
