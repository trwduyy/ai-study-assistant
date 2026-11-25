package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.idempotency.IdempotencyKey;
import com.aistudy.assistant.common.idempotency.IdempotencyKeyRepository;
import com.aistudy.assistant.common.idempotency.IdempotencyServiceImpl;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.dto.AttemptAnswerInput;
import com.aistudy.assistant.learning.dto.QuizDetailResponse;
import com.aistudy.assistant.learning.dto.QuizSubmitResultResponse;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import com.aistudy.assistant.learning.mapper.QuizMapper;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.QuestionOptionRepository;
import com.aistudy.assistant.learning.repository.QuestionRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionOptionRepository questionOptionRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyServiceImpl idempotencyService;
    private QuizServiceImpl quizService;
    private ObjectMapper objectMapper;

    private User student;
    private Course course;
    private Quiz quiz;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        idempotencyService = new IdempotencyServiceImpl(idempotencyKeyRepository, objectMapper);
        quizService = new QuizServiceImpl(
                quizRepository,
                questionRepository,
                questionOptionRepository,
                quizAttemptRepository,
                attemptAnswerRepository,
                userRepository,
                idempotencyService,
                new QuizMapper()
        );

        student = User.builder()
                .id(10L)
                .email("student@aistudy.local")
                .passwordHash("hash")
                .fullName("Sinh Viên Test")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        course = Course.builder().id(101L).owner(student).name("Mạng máy tính").build();
        quiz = Quiz.builder()
                .id(601L)
                .owner(student)
                .course(course)
                .title("Quiz Chương 3")
                .difficulty(QuizDifficulty.MEDIUM)
                .questionCount(2)
                .version(0)
                .build();

        UserPrincipal principal = UserPrincipal.create(student);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getQuizForAttempt returns questions without exposing isCorrect or explanation")
    void getQuizForAttempt_HidesAnswers() {
        Question q1 = Question.builder()
                .id(7001L)
                .quiz(quiz)
                .position(1)
                .content("Cơ chế điều tiết luồng TCP?")
                .type(QuestionType.SINGLE_CHOICE)
                .explanation("Sliding Window điều tiết kích thước cửa sổ.")
                .build();
        QuestionOption optA = QuestionOption.builder()
                .id(8001L).question(q1).label("A").content("Sliding Window").isCorrect(true).build();
        QuestionOption optB = QuestionOption.builder()
                .id(8002L).question(q1).label("B").content("ARP").isCorrect(false).build();

        when(quizRepository.findByIdAndDeletedAtIsNull(601L)).thenReturn(Optional.of(quiz));
        when(questionRepository.findByQuizIdWithDetails(601L)).thenReturn(List.of(q1));
        when(questionOptionRepository.findByQuestionIdInOrderByLabel(List.of(7001L))).thenReturn(List.of(optA, optB));

        QuizDetailResponse response = quizService.getQuizForAttempt(601L);

        assertEquals(1, response.questions().size());
        assertEquals(2, response.questions().get(0).options().size());
        assertEquals("Sliding Window", response.questions().get(0).options().get(0).content());
    }

    @Test
    @DisplayName("submitAttempt grades answers strictly on server and persists idempotency snapshot")
    void submitAttempt_GradesServerSide() {
        Question q1 = Question.builder()
                .id(7001L).quiz(quiz).position(1).content("Câu 1").type(QuestionType.SINGLE_CHOICE).explanation("Giải thích 1").build();
        Question q2 = Question.builder()
                .id(7002L).quiz(quiz).position(2).content("Câu 2").type(QuestionType.SINGLE_CHOICE).explanation("Giải thích 2").build();

        QuestionOption o1a = QuestionOption.builder().id(8001L).question(q1).label("A").content("Đúng").isCorrect(true).build();
        QuestionOption o1b = QuestionOption.builder().id(8002L).question(q1).label("B").content("Sai").isCorrect(false).build();
        QuestionOption o2a = QuestionOption.builder().id(8003L).question(q2).label("A").content("Sai").isCorrect(false).build();
        QuestionOption o2b = QuestionOption.builder().id(8004L).question(q2).label("B").content("Đúng").isCorrect(true).build();

        QuizAttempt attempt = QuizAttempt.builder()
                .id(901L)
                .quiz(quiz)
                .user(student)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now().minusSeconds(120))
                .score(BigDecimal.ZERO)
                .maxScore(new BigDecimal("10.00"))
                .totalQuestions(2)
                .version(1)
                .build();

        SubmitAttemptRequest request = new SubmitAttemptRequest(
                1,
                120,
                List.of(
                        new AttemptAnswerInput(7001L, List.of(8001L)),
                        new AttemptAnswerInput(7002L, List.of(8003L))
                )
        );

        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKeyAndEndpoint(
                eq(10L), eq("key-submit-1"), eq("POST:/api/v1/quizzes/601/attempts/901/submit")))
                .thenReturn(Optional.empty());
        when(quizAttemptRepository.findWithQuizAndUserById(901L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdWithDetails(601L)).thenReturn(List.of(q1, q2));
        when(questionOptionRepository.findByQuestionIdInOrderByLabel(List.of(7001L, 7002L)))
                .thenReturn(List.of(o1a, o1b, o2a, o2b));
        when(attemptAnswerRepository.findByAttemptId(901L)).thenReturn(List.of());
        when(attemptAnswerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(quizAttemptRepository.saveAndFlush(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(idempotencyKeyRepository.save(any(IdempotencyKey.class))).thenAnswer(inv -> inv.getArgument(0));

        QuizSubmitResultResponse result = quizService.submitAttempt(601L, 901L, "key-submit-1", request);

        assertEquals(AttemptStatus.SUBMITTED, result.status());
        assertEquals(1, result.correctCount());
        assertEquals(2, result.totalQuestions());
        assertEquals(5.0, result.score());
        assertEquals(10.0, result.maxScore());
        assertTrue(result.details().get(0).correct());
        assertFalse(result.details().get(1).correct());
        verify(idempotencyKeyRepository).save(any(IdempotencyKey.class));
    }

    @Test
    @DisplayName("submitAttempt replays saved response without re-grading when same Idempotency-Key and payload are resent")
    void submitAttempt_ReplaysCachedResponseOnDuplicateKey() throws Exception {
        SubmitAttemptRequest request = new SubmitAttemptRequest(
                1,
                120,
                List.of(new AttemptAnswerInput(7001L, List.of(8001L)))
        );
        String requestHash = idempotencyService.hashPayload(request);

        QuizSubmitResultResponse savedResult = new QuizSubmitResultResponse(
                901L,
                601L,
                AttemptStatus.SUBMITTED,
                10.0,
                10.0,
                1,
                1,
                120,
                Instant.now(),
                List.of()
        );
        IdempotencyKey cachedRecord = IdempotencyKey.builder()
                .userId(10L)
                .idempotencyKey("key-replay-1")
                .endpoint("POST:/api/v1/quizzes/601/attempts/901/submit")
                .requestHash(requestHash)
                .responseStatus(200)
                .responseBody(objectMapper.writeValueAsString(savedResult))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKeyAndEndpoint(
                eq(10L), eq("key-replay-1"), eq("POST:/api/v1/quizzes/601/attempts/901/submit")))
                .thenReturn(Optional.of(cachedRecord));

        QuizSubmitResultResponse replayed = quizService.submitAttempt(601L, 901L, "key-replay-1", request);

        assertEquals(901L, replayed.attemptId());
        assertEquals(10.0, replayed.score());
        assertEquals(AttemptStatus.SUBMITTED, replayed.status());
        verify(quizAttemptRepository, never()).findWithQuizAndUserById(any());
        verify(quizAttemptRepository, never()).saveAndFlush(any());
        verify(attemptAnswerRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("submitAttempt rejects same Idempotency-Key when payload differs")
    void submitAttempt_RejectsIdempotencyPayloadConflict() {
        SubmitAttemptRequest originalRequest = new SubmitAttemptRequest(
                1, 100, List.of(new AttemptAnswerInput(7001L, List.of(8001L))));
        SubmitAttemptRequest modifiedRequest = new SubmitAttemptRequest(
                1, 100, List.of(new AttemptAnswerInput(7001L, List.of(8002L))));

        IdempotencyKey existing = IdempotencyKey.builder()
                .userId(10L)
                .idempotencyKey("key-conflict-1")
                .endpoint("POST:/api/v1/quizzes/601/attempts/901/submit")
                .requestHash(idempotencyService.hashPayload(originalRequest))
                .responseBody("{}")
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(idempotencyKeyRepository.findByUserIdAndIdempotencyKeyAndEndpoint(
                eq(10L), eq("key-conflict-1"), eq("POST:/api/v1/quizzes/601/attempts/901/submit")))
                .thenReturn(Optional.of(existing));

        AppException ex = assertThrows(
                AppException.class,
                () -> quizService.submitAttempt(601L, 901L, "key-conflict-1", modifiedRequest)
        );
        assertEquals(ErrorCode.IDEMPOTENCY_CONFLICT, ex.getErrorCode());
    }
}
