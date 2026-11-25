package com.aistudy.assistant.assignment.service.impl;

import com.aistudy.assistant.assignment.dto.ClassTestResponse;
import com.aistudy.assistant.assignment.dto.ClassTestResultsResponse;
import com.aistudy.assistant.assignment.dto.ClassTestResultsResponse.StudentTestResultDto;
import com.aistudy.assistant.assignment.dto.ClassTestSubmitResultResponse;
import com.aistudy.assistant.assignment.dto.CreateClassTestRequest;
import com.aistudy.assistant.assignment.dto.StartClassTestAttemptResponse;
import com.aistudy.assistant.assignment.dto.UpdateClassTestRequest;
import com.aistudy.assistant.assignment.entity.ClassTest;
import com.aistudy.assistant.assignment.mapper.AssignmentMapper;
import com.aistudy.assistant.assignment.repository.ClassTestRepository;
import com.aistudy.assistant.assignment.service.ClassTestService;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.idempotency.IdempotencyService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.dto.AttemptAnswerInput;
import com.aistudy.assistant.learning.dto.QuizTakeOptionDto;
import com.aistudy.assistant.learning.dto.QuizTakeQuestionDto;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.entity.AttemptAnswer;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.QuestionOptionRepository;
import com.aistudy.assistant.learning.repository.QuestionRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Business service for timed classroom tests (FR-14) backed by {@link Quiz} and {@link QuizAttempt}.
 * Server UTC time enforces open windows, attempt deadlines (with 30s network grace), and server-side grading.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassTestServiceImpl implements ClassTestService {

    private static final BigDecimal MAX_SCORE = new BigDecimal("10.00");
    private static final long SUBMIT_NETWORK_GRACE_SECONDS = 30L;
    private static final Duration SUBMIT_IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final ClassTestRepository classTestRepository;
    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final QuestionOptionRepository questionOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final ClassroomAccessService classroomAccessService;
    private final NotificationOutboxService notificationOutboxService;
    private final IdempotencyService idempotencyService;
    private final AssignmentMapper assignmentMapper;

    @Override
    @Transactional
    public ClassTestResponse createTest(Long classId, CreateClassTestRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);
        Classroom classroom = classroomAccessService.requireClassroomExists(classId);

        validateTimeWindow(request.getStartAt(), request.getEndAt());

        Quiz quiz = quizRepository.findByIdAndDeletedAtIsNull(request.getQuizId())
                .orElseThrow(() -> new AppException(ErrorCode.QUIZ_NOT_FOUND));
        if (!quiz.getOwner().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER, "Bạn chỉ có thể tạo bài kiểm tra từ quiz do mình sở hữu.");
        }

        ClassTest classTest = ClassTest.builder()
                .classroom(classroom)
                .quiz(quiz)
                .title(request.getTitle().trim())
                .startAt(request.getStartAt())
                .endAt(request.getEndAt())
                .durationMinutes(request.getDurationMinutes())
                .maxAttempts(request.getMaxAttempts() != null ? request.getMaxAttempts() : 1)
                .shuffleQuestions(Boolean.TRUE.equals(request.getShuffleQuestions()))
                .build();

        ClassTest saved = classTestRepository.save(classTest);

        List<ClassMember> activeStudents = classMemberRepository.findActiveWithUserByClassIdAndRoles(
                classId,
                List.of(ClassRole.STUDENT)
        );
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("classId", classroom.getId());
        payload.put("className", classroom.getName());
        payload.put("testId", saved.getId());
        payload.put("title", saved.getTitle());
        payload.put("startAt", saved.getStartAt().toString());
        payload.put("endAt", saved.getEndAt().toString());
        for (ClassMember student : activeStudents) {
            notificationOutboxService.enqueueForAllChannels(
                    student.getUser(),
                    "CLASS_TEST_CREATED",
                    "CLASS_TEST",
                    saved.getId(),
                    "",
                    payload
            );
        }

        log.info("Created classTest id={} for classId={} using quizId={} by teacher userId={}",
                saved.getId(), classId, quiz.getId(), currentUserId);
        return assignmentMapper.toClassTestResponse(saved, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassTestResponse> getTests(Long classId, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        classroomAccessService.requireActiveMember(classId, currentUserId);
        return classTestRepository.findProjectedByClassIdAndUserId(classId, currentUserId, pageable);
    }

    @Override
    @Transactional
    public ClassTestResponse updateTest(Long testId, UpdateClassTestRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassTest test = classTestRepository.findWithDetailsById(testId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_TEST_NOT_FOUND));
        classroomAccessService.requireTeacherInClass(test.getClassroom().getId(), currentUserId);

        validateTimeWindow(request.getStartAt(), request.getEndAt());

        test.setTitle(request.getTitle().trim());
        test.setStartAt(request.getStartAt());
        test.setEndAt(request.getEndAt());
        test.setDurationMinutes(request.getDurationMinutes());
        if (request.getMaxAttempts() != null) {
            test.setMaxAttempts(request.getMaxAttempts());
        }
        if (request.getShuffleQuestions() != null) {
            test.setShuffleQuestions(request.getShuffleQuestions());
        }

        ClassTest saved = classTestRepository.save(test);
        long myAttemptsUsed = quizAttemptRepository.countByTestIdAndUserId(testId, currentUserId);
        log.info("Updated classTest id={} by teacher userId={}", testId, currentUserId);
        return assignmentMapper.toClassTestResponse(saved, myAttemptsUsed);
    }

    @Override
    @Transactional
    public void deleteTest(Long testId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassTest test = classTestRepository.findWithDetailsById(testId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_TEST_NOT_FOUND));
        classroomAccessService.requireTeacherInClass(test.getClassroom().getId(), currentUserId);

        classTestRepository.delete(test);
        log.info("Deleted classTest id={} by teacher userId={}", testId, currentUserId);
    }

    @Override
    @Transactional
    public StartClassTestAttemptResponse startAttempt(Long testId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassTest test = classTestRepository.findWithDetailsById(testId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_TEST_NOT_FOUND));
        classroomAccessService.requireActiveMember(test.getClassroom().getId(), currentUserId);

        Instant now = Instant.now();
        if (now.isBefore(test.getStartAt()) || now.isAfter(test.getEndAt())) {
            throw new AppException(ErrorCode.TEST_WINDOW_CLOSED);
        }

        Quiz quiz = test.getQuiz();
        if (quiz == null || quiz.isDeleted()) {
            throw new AppException(ErrorCode.QUIZ_NOT_FOUND);
        }

        long usedAttempts = quizAttemptRepository.countByTestIdAndUserId(testId, currentUserId);
        if (usedAttempts >= test.getMaxAttempts()) {
            throw new AppException(ErrorCode.MAX_ATTEMPTS_REACHED);
        }

        Instant durationDeadline = now.plus(Duration.ofMinutes(test.getDurationMinutes()));
        Instant deadlineAt = durationDeadline.isBefore(test.getEndAt()) ? durationDeadline : test.getEndAt();

        List<Question> questions = new ArrayList<>(questionRepository.findByQuizIdWithDetails(quiz.getId()));
        if (Boolean.TRUE.equals(test.getShuffleQuestions())) {
            Collections.shuffle(questions);
        }

        Map<Long, List<QuestionOption>> optionsMap = loadOptionsMap(questions);
        List<QuizTakeQuestionDto> questionDtos = questions.stream()
                .map(q -> new QuizTakeQuestionDto(
                        q.getId(),
                        q.getTopic() != null ? q.getTopic().getId() : null,
                        q.getPosition(),
                        q.getContent(),
                        q.getType(),
                        optionsMap.getOrDefault(q.getId(), List.of()).stream()
                                .map(opt -> new QuizTakeOptionDto(opt.getId(), opt.getLabel(), opt.getContent()))
                                .toList()
                ))
                .toList();

        User student = userRepository.getReferenceById(currentUserId);
        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .user(student)
                .testId(test.getId())
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .deadlineAt(deadlineAt)
                .score(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP))
                .maxScore(MAX_SCORE)
                .correctCount(0)
                .totalQuestions(questions.size())
                .build();
        QuizAttempt savedAttempt = quizAttemptRepository.save(attempt);

        log.info("Started classTest attemptId={} for testId={} studentId={}",
                savedAttempt.getId(), testId, currentUserId);
        return new StartClassTestAttemptResponse(
                savedAttempt.getId(),
                test.getId(),
                quiz.getId(),
                savedAttempt.getStartedAt(),
                savedAttempt.getDeadlineAt(),
                savedAttempt.getVersion(),
                questionDtos
        );
    }

    @Override
    @Transactional
    public ClassTestSubmitResultResponse submitAttempt(
            Long testId,
            Long attemptId,
            String idempotencyKey,
            SubmitAttemptRequest request) {
        String normalizedKey = validateAndNormalizeIdempotencyKey(idempotencyKey);
        Long currentUserId = SecurityUtils.currentUserId();
        String endpoint = "POST:/api/v1/tests/" + testId + "/attempts/" + attemptId + "/submit";
        String requestHash = idempotencyService.hashPayload(request);

        Optional<ClassTestSubmitResultResponse> cached = idempotencyService.findCachedResponse(
                currentUserId, normalizedKey, endpoint, requestHash, ClassTestSubmitResultResponse.class);
        if (cached.isPresent()) {
            return cached.get();
        }

        ClassTest test = classTestRepository.findWithDetailsById(testId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_TEST_NOT_FOUND));
        classroomAccessService.requireActiveMember(test.getClassroom().getId(), currentUserId);

        QuizAttempt attempt = quizAttemptRepository.findWithQuizAndUserByIdAndTestId(attemptId, testId)
                .orElseThrow(() -> new AppException(ErrorCode.ATTEMPT_NOT_FOUND));
        if (!attempt.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        if (attempt.getQuiz().isDeleted()) {
            throw new AppException(ErrorCode.QUIZ_NOT_FOUND);
        }
        if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
            if (normalizedKey.equals(attempt.getIdempotencyKey())) {
                return toSubmitResult(attempt, testId);
            }
            throw new AppException(ErrorCode.QUIZ_ALREADY_SUBMITTED);
        }

        Instant now = Instant.now();
        Instant hardDeadline = attempt.getDeadlineAt() != null
                ? attempt.getDeadlineAt().plusSeconds(SUBMIT_NETWORK_GRACE_SECONDS)
                : null;
        if (attempt.getStatus() == AttemptStatus.EXPIRED || (hardDeadline != null && now.isAfter(hardDeadline))) {
            throw new AppException(ErrorCode.ATTEMPT_EXPIRED);
        }
        if (request != null && request.version() != null && !request.version().equals(attempt.getVersion())) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        Long quizId = attempt.getQuiz().getId();
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

        if (request != null && request.answers() != null) {
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

        for (Question question : questions) {
            List<QuestionOption> options = optionsMap.getOrDefault(question.getId(), List.of());
            Set<Long> correctSet = options.stream()
                    .filter(QuestionOption::isCorrectOption)
                    .map(QuestionOption::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            List<Long> selectedIds = submittedByQuestion.getOrDefault(question.getId(), List.of());
            Set<Long> selectedSet = new LinkedHashSet<>(selectedIds);
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
        }

        attemptAnswerRepository.saveAll(answersToPersist);

        int durationSeconds = (request != null && request.durationSeconds() != null)
                ? request.durationSeconds()
                : (int) Math.max(0L, Duration.between(attempt.getStartedAt(), now).getSeconds());

        BigDecimal score = totalQuestions == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : MAX_SCORE.multiply(BigDecimal.valueOf(correctCount))
                        .divide(BigDecimal.valueOf(totalQuestions), 2, RoundingMode.HALF_UP);

        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(now);
        attempt.setDurationSeconds(durationSeconds);
        attempt.setCorrectCount(correctCount);
        attempt.setTotalQuestions(totalQuestions);
        attempt.setScore(score);
        attempt.setMaxScore(MAX_SCORE);
        attempt.setIdempotencyKey(normalizedKey);
        QuizAttempt saved = quizAttemptRepository.saveAndFlush(attempt);

        ClassTestSubmitResultResponse response = toSubmitResult(saved, testId);
        idempotencyService.saveResponse(
                currentUserId,
                normalizedKey,
                endpoint,
                requestHash,
                200,
                response,
                SUBMIT_IDEMPOTENCY_TTL
        );

        log.info("Submitted classTest attemptId={} testId={} score={}/{}",
                attemptId, testId, saved.getScore(), saved.getMaxScore());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public ClassTestResultsResponse getResults(Long testId) {
        Long currentUserId = SecurityUtils.currentUserId();
        ClassTest test = classTestRepository.findWithDetailsById(testId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_TEST_NOT_FOUND));
        Long classId = test.getClassroom().getId();
        classroomAccessService.requireTeacherInClass(classId, currentUserId);

        List<StudentTestResultDto> studentResults = quizAttemptRepository
                .findClassTestStudentResults(classId, testId);

        long totalStudents = studentResults.size();
        List<Double> submittedScores = studentResults.stream()
                .map(StudentTestResultDto::bestScore)
                .filter(Objects::nonNull)
                .toList();

        long submittedCount = submittedScores.size();
        double averageScore = submittedCount == 0
                ? 0.0
                : roundTwoDecimals(submittedScores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0));
        double highestScore = submittedCount == 0
                ? 0.0
                : submittedScores.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        double lowestScore = submittedCount == 0
                ? 0.0
                : submittedScores.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);

        return new ClassTestResultsResponse(
                test.getId(),
                classId,
                test.getTitle(),
                totalStudents,
                submittedCount,
                averageScore,
                highestScore,
                lowestScore,
                studentResults
        );
    }

    private ClassTestSubmitResultResponse toSubmitResult(QuizAttempt attempt, Long testId) {
        return new ClassTestSubmitResultResponse(
                attempt.getId(),
                testId,
                attempt.getQuiz().getId(),
                attempt.getStatus(),
                attempt.getScore().doubleValue(),
                attempt.getMaxScore().doubleValue(),
                attempt.getCorrectCount(),
                attempt.getTotalQuestions(),
                attempt.getDurationSeconds(),
                attempt.getSubmittedAt()
        );
    }

    private void validateTimeWindow(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !endAt.isAfter(startAt)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian kết thúc bài kiểm tra phải sau thời gian bắt đầu.",
                    List.of(new FieldErrorDetail("endAt", "Thời gian kết thúc phải lớn hơn thời gian bắt đầu."))
            );
        }
    }

    private String validateAndNormalizeIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header Idempotency-Key là bắt buộc khi nộp bài.");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Idempotency-Key không được vượt quá 64 ký tự.");
        }
        return trimmed;
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

    private double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
