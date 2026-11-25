package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.GradeSubmissionRequest;
import com.aistudy.assistant.assignment.dto.ReopenSubmissionRequest;
import com.aistudy.assistant.assignment.dto.SubmissionResponse;
import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.assignment.entity.Submission;
import com.aistudy.assistant.assignment.entity.SubmissionStatus;
import com.aistudy.assistant.assignment.mapper.AssignmentMapper;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.assignment.repository.SubmissionFileRepository;
import com.aistudy.assistant.assignment.repository.SubmissionRepository;
import com.aistudy.assistant.assignment.service.impl.SubmissionServiceImpl;
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
import com.aistudy.assistant.learning.service.FileStorageService;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentSubmissionServiceTest {

    @Mock
    private AssignmentRepository assignmentRepository;

    @Mock
    private SubmissionRepository submissionRepository;

    @Mock
    private SubmissionFileRepository submissionFileRepository;

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

    @Mock
    private FileStorageService fileStorageService;

    private SubmissionServiceImpl submissionService;

    private User teacherUser;
    private User studentUser;
    private Classroom classroom;
    private ClassMember studentMember;
    private ClassMember teacherMember;
    private Assignment openAssignment;

    @BeforeEach
    void setUp() {
        TransactionTemplate immediateTxTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(new SimpleTransactionStatus());
            }
        };

        submissionService = new SubmissionServiceImpl(
                assignmentRepository,
                submissionRepository,
                submissionFileRepository,
                classMemberRepository,
                userRepository,
                classroomAccessService,
                notificationOutboxService,
                idempotencyService,
                fileStorageService,
                new AssignmentMapper(),
                immediateTxTemplate
        );

        teacherUser = User.builder()
                .id(1L)
                .email("gv01@actvn.edu.vn")
                .fullName("TS. Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        studentUser = User.builder()
                .id(10L)
                .email("sv01@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        classroom = Classroom.builder()
                .id(100L)
                .code("KMA7N9P4")
                .name("Lập trình Mạng")
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

        teacherMember = ClassMember.builder()
                .id(1002L)
                .classroom(classroom)
                .user(teacherUser)
                .classRole(ClassRole.OWNER_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        openAssignment = Assignment.builder()
                .id(401L)
                .classroom(classroom)
                .creator(teacherUser)
                .title("Bài tập tuần 3")
                .maxScore(new BigDecimal("10.00"))
                .openAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .dueAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .allowLate(false)
                .maxSubmissions(1)
                .version(0)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("submitAssignment before dueAt succeeds with late=false and writes outbox")
    void submitAssignment_OnTime_SuccessNotLate() {
        authenticateAs(studentUser);
        MockMultipartFile file = new MockMultipartFile(
                "files", "report.pdf", "application/pdf", "pdf-bytes".getBytes());

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-1");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-1"), anyString(), eq("req-hash-1"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(submissionRepository.findByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(fileStorageService.storeFile(file, 10L)).thenReturn(
                new FileStorageService.StoredFile("report.pdf", "application/pdf", 9L, "10/uuid_report.pdf", Path.of("uploads/10/uuid_report.pdf"), "sha")
        );
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(990L);
            s.setVersion(0);
            return s;
        });
        when(submissionFileRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(eq(100L), any())).thenReturn(List.of(teacherMember));

        SubmissionResponse response = submissionService.submitAssignment(401L, "key-1", "Bài làm", List.of(file));

        assertEquals(990L, response.getId());
        assertEquals(SubmissionStatus.SUBMITTED, response.getStatus());
        assertFalse(response.getLate());
        assertEquals(1, response.getFiles().size());
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(teacherUser), eq("ASSIGNMENT_SUBMITTED"), eq("SUBMISSION"), eq(990L), anyString(), anyMap()
        );
        verify(idempotencyService).saveResponse(eq(10L), eq("key-1"), anyString(), eq("req-hash-1"), eq(201), any(), any());
    }

    @Test
    @DisplayName("submitAssignment past dueAt with allowLate=false throws 422 ASSIGNMENT_CLOSED")
    void submitAssignment_PastDueAndDisallowLate_ThrowsAssignmentClosed() {
        authenticateAs(studentUser);
        openAssignment.setDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
        openAssignment.setAllowLate(false);

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-late");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-late"), anyString(), eq("req-hash-late"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());

        AppException ex = assertThrows(
                AppException.class,
                () -> submissionService.submitAssignment(401L, "key-late", "Nộp muộn", List.of())
        );
        assertEquals(ErrorCode.ASSIGNMENT_CLOSED, ex.getErrorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getErrorCode().getStatus());
    }

    @Test
    @DisplayName("submitAssignment past dueAt with allowLate=true succeeds and marks late=true")
    void submitAssignment_PastDueAndAllowLate_MarksLateTrue() {
        authenticateAs(studentUser);
        openAssignment.setDueAt(Instant.now().minus(1, ChronoUnit.HOURS));
        openAssignment.setAllowLate(true);

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-late-ok");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-late-ok"), anyString(), eq("req-hash-late-ok"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(submissionRepository.findByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(991L);
            s.setVersion(0);
            return s;
        });
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(eq(100L), any())).thenReturn(List.of(teacherMember));

        SubmissionResponse response = submissionService.submitAssignment(401L, "key-late-ok", "Nộp muộn hợp lệ", List.of());

        assertTrue(response.getLate());
        assertEquals(SubmissionStatus.SUBMITTED, response.getStatus());
    }

    @Test
    @DisplayName("Consecutive submission with same Idempotency-Key returns cached response without duplicate save")
    void submitAssignment_ConsecutiveSameIdempotencyKey_ReturnsCachedOnce() {
        authenticateAs(studentUser);
        SubmissionResponse cachedResponse = SubmissionResponse.builder()
                .id(990L)
                .assignmentId(401L)
                .studentId(10L)
                .status(SubmissionStatus.SUBMITTED)
                .late(false)
                .version(0)
                .build();

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-idem");
        when(idempotencyService.findCachedResponse(eq(10L), eq("idem-uuid-1"), anyString(), eq("req-hash-idem"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(cachedResponse));

        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(submissionRepository.findByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            s.setId(990L);
            s.setVersion(0);
            return s;
        });
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(eq(100L), any())).thenReturn(List.of(teacherMember));

        SubmissionResponse first = submissionService.submitAssignment(401L, "idem-uuid-1", "Lần 1", List.of());
        SubmissionResponse second = submissionService.submitAssignment(401L, "idem-uuid-1", "Lần 1", List.of());

        assertEquals(first.getId(), second.getId());
        verify(submissionRepository, times(1)).saveAndFlush(any(Submission.class));
        verify(idempotencyService, times(1)).saveResponse(any(), anyString(), anyString(), anyString(), anyInt(), any(), any());
    }

    @Test
    @DisplayName("Duplicate submission with different key throws 409 ASSIGNMENT_ALREADY_SUBMITTED")
    void submitAssignment_DifferentKeyWhenAlreadySubmitted_ThrowsConflict() {
        authenticateAs(studentUser);
        Submission existing = Submission.builder()
                .id(990L)
                .assignment(openAssignment)
                .student(studentUser)
                .status(SubmissionStatus.SUBMITTED)
                .idempotencyKey("key-first")
                .build();

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-2");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-second"), anyString(), eq("req-hash-2"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L))
                .thenReturn(Optional.of(existing));

        AppException ex = assertThrows(
                AppException.class,
                () -> submissionService.submitAssignment(401L, "key-second", "Nộp lần 2", List.of())
        );
        assertEquals(ErrorCode.ASSIGNMENT_ALREADY_SUBMITTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("Unique constraint violation on concurrent inserts maps to 409 ASSIGNMENT_ALREADY_SUBMITTED")
    void submitAssignment_ConcurrentUniqueViolation_ThrowsConflict() {
        authenticateAs(studentUser);

        when(idempotencyService.hashPayload(any())).thenReturn("req-hash-race");
        when(idempotencyService.findCachedResponse(eq(10L), eq("key-race"), anyString(), eq("req-hash-race"), eq(SubmissionResponse.class)))
                .thenReturn(Optional.empty());
        when(assignmentRepository.findWithDetailsById(401L)).thenReturn(Optional.of(openAssignment));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(studentMember);
        when(submissionRepository.findWithDetailsByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(submissionRepository.findByAssignmentIdAndStudentId(401L, 10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(submissionRepository.saveAndFlush(any(Submission.class)))
                .thenThrow(new DataIntegrityViolationException("uk_submissions_assignment_student"));

        AppException ex = assertThrows(
                AppException.class,
                () -> submissionService.submitAssignment(401L, "key-race", "Race submit", List.of())
        );
        assertEquals(ErrorCode.ASSIGNMENT_ALREADY_SUBMITTED, ex.getErrorCode());
    }

    @Test
    @DisplayName("gradeSubmission throws 400 VALIDATION_ERROR when score exceeds assignment maxScore")
    void gradeSubmission_ScoreExceedsMaxScore_ThrowsBadRequest() {
        authenticateAs(teacherUser);
        Submission submission = Submission.builder()
                .id(990L)
                .assignment(openAssignment)
                .student(studentUser)
                .status(SubmissionStatus.SUBMITTED)
                .version(0)
                .build();

        when(submissionRepository.findWithDetailsById(990L)).thenReturn(Optional.of(submission));
        when(classroomAccessService.requireTeacherInClass(100L, 1L)).thenReturn(teacherMember);

        GradeSubmissionRequest request = GradeSubmissionRequest.builder()
                .score(new BigDecimal("10.50"))
                .feedback("Quá điểm tối đa")
                .status(SubmissionStatus.GRADED)
                .build();

        AppException ex = assertThrows(
                AppException.class,
                () -> submissionService.gradeSubmission(990L, request, null)
        );
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getErrorCode().getStatus());
    }

    @Test
    @DisplayName("reopenSubmission resets grading fields and notifies student via outbox")
    void reopenSubmission_Success_ResetsFieldsAndNotifies() {
        authenticateAs(teacherUser);
        Submission graded = Submission.builder()
                .id(990L)
                .assignment(openAssignment)
                .student(studentUser)
                .status(SubmissionStatus.GRADED)
                .score(new BigDecimal("7.50"))
                .gradedBy(teacherUser)
                .gradedAt(Instant.now())
                .idempotencyKey("old-key")
                .version(1)
                .build();

        when(submissionRepository.findWithDetailsById(990L)).thenReturn(Optional.of(graded));
        when(classroomAccessService.requireTeacherInClass(100L, 1L)).thenReturn(teacherMember);
        when(submissionRepository.saveAndFlush(any(Submission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(submissionFileRepository.findBySubmissionIdOrderByIdAsc(990L)).thenReturn(List.of());

        SubmissionResponse response = submissionService.reopenSubmission(
                990L,
                ReopenSubmissionRequest.builder().reason("Nộp bổ sung file PDF").version(1).build(),
                null
        );

        assertEquals(SubmissionStatus.RETURNED, response.getStatus());
        assertTrue(response.getReopened());
        assertNull(response.getScore());
        assertNull(response.getGradedAt());
        assertNotNull(response.getFeedback());
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser), eq("ASSIGNMENT_REOPENED"), eq("SUBMISSION"), eq(990L), anyString(), anyMap()
        );
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
