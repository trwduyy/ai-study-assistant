package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.ClassTestSubmitResultResponse;
import com.aistudy.assistant.assignment.dto.StartClassTestAttemptResponse;
import com.aistudy.assistant.assignment.entity.ClassTest;
import com.aistudy.assistant.assignment.mapper.AssignmentMapper;
import com.aistudy.assistant.assignment.repository.ClassTestRepository;
import com.aistudy.assistant.assignment.service.impl.ClassTestServiceImpl;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.idempotency.IdempotencyService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.dto.AttemptAnswerInput;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Question;
import com.aistudy.assistant.learning.entity.QuestionOption;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.aistudy.assistant.learning.entity.Quiz;
import com.aistudy.assistant.learning.entity.QuizAttempt;
import com.aistudy.assistant.learning.entity.QuizDifficulty;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.QuestionOptionRepository;
import com.aistudy.assistant.learning.repository.QuestionRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassTestServiceTest {

    @Mock
    private ClassTestRepository classTestRepository;
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
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassroomAccessService classroomAccessService;
    @Mock
    private NotificationOutboxService notificationOutboxService;
    @Mock
    private IdempotencyService idempotencyService;

    @Spy
    private AssignmentMapper assignmentMapper = new AssignmentMapper();

    @InjectMocks
    private ClassTestServiceImpl classTestService;

    private User teacherUser;
    private User studentUser;
    private Classroom classroom;
    private ClassMember studentMember;
    private Quiz quiz;
    private ClassTest activeTest;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder()
                .id(1L)
                .email("gv@actvn.edu.vn")
                .fullName("GV")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        studentUser = User.builder()
                .id(10L)
                .email("sv@actvn.edu.vn")
                .fullName("SV")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        classroom = Classroom.builder()
                .id(100L)
                .code("KMA7N9P4")
                .name("Mạng máy tính")
                .status(ClassroomStatus.OPEN)
                .owner(teacherUser)
                .build();

        studentMember = ClassMember.builder()
                .id(1001L)
                .classroom(classroom)
                .user(studentUser)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        quiz = Quiz.builder()
                .id(601L)
                .owner(teacherUser)
                .title("Quiz TCP")
                .difficulty(QuizDifficulty.MEDIUM)
                .questionCount(1)
                .build();

        activeTest = ClassTest.builder()
                .id(701L)
                .classroom(classroom)
                .quiz(quiz)
                .title("Kiểm tra 15 phút")
                .startAt(Instant.now().minus(10, ChronoUnit.MINUTES))
                .endAt(Instant.now().plus(50, ChronoUnit.MINUTES))
                .durationMinutes(15)
                .maxAttempts(1)
                .shuffleQuestions(false)
                .build();

        UserPrincipal principal = UserPrincipal.create(studentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("startAttempt outside [startAt, endAt] throws 422 TEST_WINDOW_CLOSED")
    void startAttempt_OutsideWindow_ThrowsTestWindowClosed() {
        activeTest.setStartAt(Instant.now().plus(1, ChronoUnit.HOURS));
        activeTest.setEndAt(Instant.now().plus(2, ChronoUnit.HOURS));

        when(classTestRepository.findWithDetailsById(701L)).thenReturn(Optional.of(activeTest));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);

        AppException ex = assertThrows(AppException.class, () -> classTestService.startAttempt(701L));
        assertEquals(ErrorCode.TEST_WINDOW_CLOSED, ex.getErrorCode());
    }

    @Test
    @DisplayName("startAttempt when maxAttempts reached throws 422 MAX_ATTEMPTS_REACHED")
    void startAttempt_MaxAttemptsReached_ThrowsMaxAttemptsReached() {
        when(classTestRepository.findWithDetailsById(701L)).thenReturn(Optional.of(activeTest));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(quizAttemptRepository.countByTestIdAndUserId(701L, 10L)).thenReturn(1L);

        AppException ex = assertThrows(AppException.class, () -> classTestService.startAttempt(701L));
        assertEquals(ErrorCode.MAX_ATTEMPTS_REACHED, ex.getErrorCode());
    }

    @Test
    @DisplayName("startAttempt inside window creates QuizAttempt with bounded deadline and hides correct answers")
    void startAttempt_WithinWindow_Success() {
        Question q1 = Question.builder()
                .id(7001L)
                .quiz(quiz)
                .position(1)
                .content("TCP dùng cổng nào?")
                .type(QuestionType.SINGLE_CHOICE)
                .explanation("Giải thích bí mật")
                .build();
        QuestionOption optA = QuestionOption.builder().id(8001L).question(q1).label("A").content("UDP").isCorrect(false).build();
        QuestionOption optB = QuestionOption.builder().id(8002L).question(q1).label("B").content("TCP").isCorrect(true).build();

        when(classTestRepository.findWithDetailsById(701L)).thenReturn(Optional.of(activeTest));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(quizAttemptRepository.countByTestIdAndUserId(701L, 10L)).thenReturn(0L);
        when(questionRepository.findByQuizIdWithDetails(601L)).thenReturn(List.of(q1));
        when(questionOptionRepository.findByQuestionIdInOrderByLabel(List.of(7001L))).thenReturn(List.of(optA, optB));
        when(userRepository.getReferenceById(10L)).thenReturn(studentUser);
        when(quizAttemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
            QuizAttempt qa = inv.getArgument(0);
            qa.setId(950L);
            qa.setVersion(0);
            return qa;
        });

        StartClassTestAttemptResponse response = classTestService.startAttempt(701L);

        assertEquals(950L, response.attemptId());
        assertEquals(701L, response.testId());
        assertNotNull(response.deadlineAt());
        assertTrue(!response.deadlineAt().isAfter(activeTest.getEndAt()));
        assertEquals(1, response.questions().size());
        assertEquals(2, response.questions().get(0).options().size());
    }

    @Test
    @DisplayName("submitAttempt past deadlineAt + 30s grace throws 422 ATTEMPT_EXPIRED")
    void submitAttempt_ExpiredDeadline_ThrowsAttemptExpired() {
        QuizAttempt expiredAttempt = QuizAttempt.builder()
                .id(950L)
                .quiz(quiz)
                .user(studentUser)
                .testId(701L)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now().minus(20, ChronoUnit.MINUTES))
                .deadlineAt(Instant.now().minus(60, ChronoUnit.SECONDS))
                .version(0)
                .build();

        when(idempotencyService.hashPayload(any())).thenReturn("hash-exp");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-exp"), anyString(), eq("hash-exp"), eq(ClassTestSubmitResultResponse.class)))
                .thenReturn(Optional.empty());
        when(classTestRepository.findWithDetailsById(701L)).thenReturn(Optional.of(activeTest));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(quizAttemptRepository.findWithQuizAndUserByIdAndTestId(950L, 701L)).thenReturn(Optional.of(expiredAttempt));

        SubmitAttemptRequest request = new SubmitAttemptRequest(0, 1200, List.of());
        AppException ex = assertThrows(
                AppException.class,
                () -> classTestService.submitAttempt(701L, 950L, "key-exp", request)
        );
        assertEquals(ErrorCode.ATTEMPT_EXPIRED, ex.getErrorCode());
    }

    @Test
    @DisplayName("submitAttempt within deadline grades server-side and returns score")
    void submitAttempt_Valid_GradesServerSide() {
        Question q1 = Question.builder()
                .id(7001L)
                .quiz(quiz)
                .position(1)
                .content("Câu 1")
                .type(QuestionType.SINGLE_CHOICE)
                .build();
        QuestionOption optA = QuestionOption.builder().id(8001L).question(q1).label("A").content("Sai").isCorrect(false).build();
        QuestionOption optB = QuestionOption.builder().id(8002L).question(q1).label("B").content("Đúng").isCorrect(true).build();

        QuizAttempt attempt = QuizAttempt.builder()
                .id(950L)
                .quiz(quiz)
                .user(studentUser)
                .testId(701L)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .deadlineAt(Instant.now().plus(10, ChronoUnit.MINUTES))
                .score(BigDecimal.ZERO)
                .maxScore(new BigDecimal("10.00"))
                .version(0)
                .build();

        when(idempotencyService.hashPayload(any())).thenReturn("hash-ok");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-ok"), anyString(), eq("hash-ok"), eq(ClassTestSubmitResultResponse.class)))
                .thenReturn(Optional.empty());
        when(classTestRepository.findWithDetailsById(701L)).thenReturn(Optional.of(activeTest));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(quizAttemptRepository.findWithQuizAndUserByIdAndTestId(950L, 701L)).thenReturn(Optional.of(attempt));
        when(questionRepository.findByQuizIdWithDetails(601L)).thenReturn(List.of(q1));
        when(questionOptionRepository.findByQuestionIdInOrderByLabel(List.of(7001L))).thenReturn(List.of(optA, optB));
        when(attemptAnswerRepository.findByAttemptId(950L)).thenReturn(List.of());
        when(quizAttemptRepository.saveAndFlush(any(QuizAttempt.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitAttemptRequest request = new SubmitAttemptRequest(
                0,
                300,
                List.of(new AttemptAnswerInput(7001L, List.of(8002L)))
        );

        ClassTestSubmitResultResponse result = classTestService.submitAttempt(701L, 950L, "key-ok", request);

        assertEquals(AttemptStatus.SUBMITTED, result.status());
        assertEquals(10.0, result.score());
        assertEquals(1, result.correctCount());
        assertEquals(1, result.totalQuestions());
    }
}
