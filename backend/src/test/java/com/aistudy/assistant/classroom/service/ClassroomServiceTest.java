package com.aistudy.assistant.classroom.service;

import com.aistudy.assistant.classroom.dto.AddCoTeacherRequest;
import com.aistudy.assistant.classroom.dto.ClassJoinRequestResponse;
import com.aistudy.assistant.classroom.dto.ClassroomResponse;
import com.aistudy.assistant.classroom.dto.CreateClassroomRequest;
import com.aistudy.assistant.classroom.dto.CreateJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.DecideJoinRequestRequest;
import com.aistudy.assistant.classroom.dto.UpdateClassroomRequest;
import com.aistudy.assistant.classroom.entity.ClassJoinRequest;
import com.aistudy.assistant.classroom.entity.ClassMaterial;
import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.entity.JoinRequestDecision;
import com.aistudy.assistant.classroom.entity.JoinRequestStatus;
import com.aistudy.assistant.classroom.mapper.ClassroomMapper;
import com.aistudy.assistant.classroom.repository.ClassJoinRequestRepository;
import com.aistudy.assistant.classroom.repository.ClassMaterialRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassPostRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.classroom.service.impl.ClassroomServiceImpl;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassroomServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;

    @Mock
    private ClassMemberRepository classMemberRepository;

    @Mock
    private ClassJoinRequestRepository classJoinRequestRepository;

    @Mock
    private ClassPostRepository classPostRepository;

    @Mock
    private ClassMaterialRepository classMaterialRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClassroomAccessService classroomAccessService;

    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ClassroomServiceImpl classroomService;

    private User teacherUser;
    private User coTeacherUser;
    private User studentUser;
    private User outsiderUser;
    private Classroom openClassroom;
    private ClassMember ownerMembership;

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

        classroomService = new ClassroomServiceImpl(
                classroomRepository,
                classMemberRepository,
                classJoinRequestRepository,
                classPostRepository,
                classMaterialRepository,
                userRepository,
                classroomAccessService,
                notificationOutboxService,
                fileStorageService,
                new ClassroomMapper(),
                immediateTxTemplate,
                eventPublisher
        );

        teacherUser = User.builder()
                .id(1L)
                .email("gv01@actvn.edu.vn")
                .fullName("TS. Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        coTeacherUser = User.builder()
                .id(2L)
                .email("gv02@actvn.edu.vn")
                .fullName("ThS. Lê Thị C")
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

        outsiderUser = User.builder()
                .id(20L)
                .email("outsider@actvn.edu.vn")
                .fullName("Người Ngoài Lớp")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        openClassroom = Classroom.builder()
                .id(100L)
                .code("KMA7N9P4")
                .name("Lập trình Mạng - K19A")
                .subject("Mạng máy tính")
                .status(ClassroomStatus.OPEN)
                .owner(teacherUser)
                .version(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        ownerMembership = ClassMember.builder()
                .id(1000L)
                .classroom(openClassroom)
                .user(teacherUser)
                .classRole(ClassRole.OWNER_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .joinedAt(Instant.now())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createClassroom generates unique 8-char code and adds creator as OWNER_TEACHER")
    void createClassroom_GeneratesCodeAndAddsOwnerMember() {
        authenticateAs(teacherUser);
        CreateClassroomRequest request = CreateClassroomRequest.builder()
                .name("Lập trình Mạng - K19A")
                .subject("Mạng máy tính")
                .description("Mô tả lớp")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(teacherUser));
        when(classroomRepository.existsByCode(anyString())).thenReturn(false);
        when(classroomRepository.save(any(Classroom.class))).thenAnswer(inv -> {
            Classroom c = inv.getArgument(0);
            c.setId(100L);
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(Instant.now());
            return c;
        });

        ClassroomResponse response = classroomService.createClassroom(request);

        assertNotNull(response.getCode());
        assertEquals(8, response.getCode().length());
        assertTrue(response.getCode().matches("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{8}$"));
        assertEquals(ClassroomStatus.OPEN, response.getStatus());
        assertEquals(ClassRole.OWNER_TEACHER, response.getMyClassRole());
        assertEquals(1L, response.getMemberCount());

        ArgumentCaptor<ClassMember> memberCaptor = ArgumentCaptor.forClass(ClassMember.class);
        verify(classMemberRepository).save(memberCaptor.capture());
        assertEquals(ClassRole.OWNER_TEACHER, memberCaptor.getValue().getClassRole());
        assertEquals(ClassMemberStatus.ACTIVE, memberCaptor.getValue().getStatus());
        assertEquals(1L, memberCaptor.getValue().getUser().getId());
    }

    @Test
    @DisplayName("requestJoin returns 409 JOIN_REQUEST_EXISTS when student already has PENDING request")
    void requestJoin_DuplicatePendingRequest_ThrowsConflict() {
        authenticateAs(studentUser);
        CreateJoinRequestRequest request = new CreateJoinRequestRequest("kma7n9p4");

        when(classroomRepository.findByCode("KMA7N9P4")).thenReturn(Optional.of(openClassroom));
        when(classMemberRepository.findByClassroomIdAndUserId(100L, 10L)).thenReturn(Optional.empty());
        when(classJoinRequestRepository.existsByClassroomIdAndStudentIdAndStatus(100L, 10L, JoinRequestStatus.PENDING))
                .thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> classroomService.requestJoin(request));
        assertEquals(ErrorCode.JOIN_REQUEST_EXISTS, ex.getErrorCode());
        verify(classJoinRequestRepository, never()).saveAndFlush(any());
        verify(notificationOutboxService, never()).enqueueForAllChannels(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    @DisplayName("requestJoin translates MySQL unique constraint violation into 409 JOIN_REQUEST_EXISTS")
    void requestJoin_ConcurrentInsertConstraintViolation_ThrowsConflict() {
        authenticateAs(studentUser);
        CreateJoinRequestRequest request = new CreateJoinRequestRequest("KMA7N9P4");

        when(classroomRepository.findByCode("KMA7N9P4")).thenReturn(Optional.of(openClassroom));
        when(classMemberRepository.findByClassroomIdAndUserId(100L, 10L)).thenReturn(Optional.empty());
        when(classJoinRequestRepository.existsByClassroomIdAndStudentIdAndStatus(100L, 10L, JoinRequestStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(classJoinRequestRepository.saveAndFlush(any(ClassJoinRequest.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry for uk_class_join_requests_pending"));

        AppException ex = assertThrows(AppException.class, () -> classroomService.requestJoin(request));
        assertEquals(ErrorCode.JOIN_REQUEST_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("requestJoin succeeds and enqueues notification_outbox for class teachers")
    void requestJoin_Success_CreatesPendingRequestAndNotifiesTeachers() {
        authenticateAs(studentUser);
        CreateJoinRequestRequest request = new CreateJoinRequestRequest("KMA7N9P4");

        when(classroomRepository.findByCode("KMA7N9P4")).thenReturn(Optional.of(openClassroom));
        when(classMemberRepository.findByClassroomIdAndUserId(100L, 10L)).thenReturn(Optional.empty());
        when(classJoinRequestRepository.existsByClassroomIdAndStudentIdAndStatus(100L, 10L, JoinRequestStatus.PENDING))
                .thenReturn(false);
        when(userRepository.findById(10L)).thenReturn(Optional.of(studentUser));
        when(classJoinRequestRepository.saveAndFlush(any(ClassJoinRequest.class))).thenAnswer(inv -> {
            ClassJoinRequest jr = inv.getArgument(0);
            jr.setId(501L);
            jr.setCreatedAt(Instant.now());
            jr.setUpdatedAt(Instant.now());
            return jr;
        });
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(
                eq(100L),
                eq(List.of(ClassRole.OWNER_TEACHER, ClassRole.CO_TEACHER))
        )).thenReturn(List.of(ownerMembership));

        ClassJoinRequestResponse response = classroomService.requestJoin(request);

        assertEquals(501L, response.getId());
        assertEquals(JoinRequestStatus.PENDING, response.getStatus());
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(teacherUser),
                eq("CLASS_JOIN_REQUEST_CREATED"),
                eq("CLASS_JOIN_REQUEST"),
                eq(501L),
                eq(""),
                anyMap()
        );
    }

    @Test
    @DisplayName("requestJoin returns 404 CLASS_CODE_INVALID when classroom is CLOSED or code does not exist")
    void requestJoin_ClosedClass_ThrowsClassCodeInvalid() {
        authenticateAs(studentUser);
        openClassroom.setStatus(ClassroomStatus.CLOSED);
        when(classroomRepository.findByCode("KMA7N9P4")).thenReturn(Optional.of(openClassroom));

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomService.requestJoin(new CreateJoinRequestRequest("KMA7N9P4"))
        );
        assertEquals(ErrorCode.CLASS_CODE_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("decideJoinRequest APPROVED adds ACTIVE student member and writes notification_outbox")
    void decideJoinRequest_Approved_AddsMemberAndEnqueuesOutbox() {
        authenticateAs(teacherUser);
        ClassJoinRequest pendingRequest = ClassJoinRequest.builder()
                .id(501L)
                .classroom(openClassroom)
                .student(studentUser)
                .status(JoinRequestStatus.PENDING)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(classroomAccessService.requireTeacherInClass(100L, 1L)).thenReturn(ownerMembership);
        when(classJoinRequestRepository.findWithDetailsByIdAndClassroomId(501L, 100L))
                .thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.findByClassroomIdAndUserId(100L, 10L)).thenReturn(Optional.empty());
        when(classJoinRequestRepository.save(any(ClassJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        DecideJoinRequestRequest decision = DecideJoinRequestRequest.builder()
                .decision(JoinRequestDecision.APPROVED)
                .build();

        ClassJoinRequestResponse response = classroomService.decideJoinRequest(100L, 501L, decision);

        assertEquals(JoinRequestStatus.APPROVED, response.getStatus());
        assertEquals(1L, response.getDecidedById());
        assertNotNull(response.getDecidedAt());

        ArgumentCaptor<ClassMember> memberCaptor = ArgumentCaptor.forClass(ClassMember.class);
        verify(classMemberRepository).save(memberCaptor.capture());
        assertEquals(ClassRole.STUDENT, memberCaptor.getValue().getClassRole());
        assertEquals(ClassMemberStatus.ACTIVE, memberCaptor.getValue().getStatus());
        assertEquals(10L, memberCaptor.getValue().getUser().getId());

        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser),
                eq("CLASS_JOIN_REQUEST_APPROVED"),
                eq("CLASS_JOIN_REQUEST"),
                eq(501L),
                eq(""),
                payloadCaptor.capture()
        );
        assertEquals("APPROVED", payloadCaptor.getValue().get("decision"));
        assertEquals(100L, payloadCaptor.getValue().get("classId"));
    }

    @Test
    @DisplayName("decideJoinRequest APPROVED reactivates previously REMOVED student membership")
    void decideJoinRequest_Approved_ReactivatesRemovedMember() {
        authenticateAs(teacherUser);
        ClassJoinRequest pendingRequest = ClassJoinRequest.builder()
                .id(502L)
                .classroom(openClassroom)
                .student(studentUser)
                .status(JoinRequestStatus.PENDING)
                .build();

        ClassMember removedMembership = ClassMember.builder()
                .id(1005L)
                .classroom(openClassroom)
                .user(studentUser)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.REMOVED)
                .joinedAt(Instant.now().minusSeconds(3600))
                .build();

        when(classroomAccessService.requireTeacherInClass(100L, 1L)).thenReturn(ownerMembership);
        when(classJoinRequestRepository.findWithDetailsByIdAndClassroomId(502L, 100L))
                .thenReturn(Optional.of(pendingRequest));
        when(userRepository.findById(1L)).thenReturn(Optional.of(teacherUser));
        when(classMemberRepository.findByClassroomIdAndUserId(100L, 10L))
                .thenReturn(Optional.of(removedMembership));
        when(classJoinRequestRepository.save(any(ClassJoinRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        classroomService.decideJoinRequest(
                100L,
                502L,
                DecideJoinRequestRequest.builder().decision(JoinRequestDecision.APPROVED).build()
        );

        assertEquals(ClassMemberStatus.ACTIVE, removedMembership.getStatus());
        assertEquals(ClassRole.STUDENT, removedMembership.getClassRole());
        verify(classMemberRepository).save(removedMembership);
    }

    @Test
    @DisplayName("Outsider or removed student cannot list or download class materials (403 NOT_CLASS_MEMBER)")
    void classMaterials_NonMemberOrRemovedStudent_ThrowsForbidden() {
        authenticateAs(outsiderUser);

        ClassMaterial material = ClassMaterial.builder()
                .id(301L)
                .classroom(openClassroom)
                .uploader(teacherUser)
                .fileName("Bai-giang-TCP.pdf")
                .storedPath("1/uuid_Bai-giang-TCP.pdf")
                .mimeType("application/pdf")
                .sizeBytes(2048L)
                .build();

        doThrow(new AppException(ErrorCode.NOT_CLASS_MEMBER))
                .when(classroomAccessService)
                .requireActiveMember(100L, 20L);

        AppException listEx = assertThrows(
                AppException.class,
                () -> classroomService.getMaterials(100L, PageRequest.of(0, 20))
        );
        assertEquals(ErrorCode.NOT_CLASS_MEMBER, listEx.getErrorCode());

        when(classMaterialRepository.findWithClassroomById(301L)).thenReturn(Optional.of(material));
        AppException downloadEx = assertThrows(
                AppException.class,
                () -> classroomService.downloadMaterial(301L)
        );
        assertEquals(ErrorCode.NOT_CLASS_MEMBER, downloadEx.getErrorCode());
        verify(fileStorageService, never()).loadFileAsResource(anyString());
    }

    @Test
    @DisplayName("Active student can download class material successfully")
    void downloadMaterial_ActiveStudent_Success() {
        authenticateAs(studentUser);

        ClassMaterial material = ClassMaterial.builder()
                .id(301L)
                .classroom(openClassroom)
                .uploader(teacherUser)
                .fileName("Bai-giang-TCP.pdf")
                .storedPath("1/uuid_Bai-giang-TCP.pdf")
                .mimeType("application/pdf")
                .sizeBytes(2048L)
                .build();

        ClassMember activeStudent = ClassMember.builder()
                .id(1010L)
                .classroom(openClassroom)
                .user(studentUser)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        Resource mockResource = new ByteArrayResource("PDF".getBytes());

        when(classMaterialRepository.findWithClassroomById(301L)).thenReturn(Optional.of(material));
        when(classroomAccessService.requireActiveMember(100L, 10L)).thenReturn(activeStudent);
        when(fileStorageService.loadFileAsResource("1/uuid_Bai-giang-TCP.pdf")).thenReturn(mockResource);

        ClassroomService.ClassMaterialDownload download = classroomService.downloadMaterial(301L);

        assertEquals("Bai-giang-TCP.pdf", download.fileName());
        assertEquals("application/pdf", download.mimeType());
        assertEquals(mockResource, download.resource());
    }

    @Test
    @DisplayName("updateClassroom throws 409 VERSION_CONFLICT when version mismatches")
    void updateClassroom_VersionMismatch_ThrowsConflict() {
        authenticateAs(teacherUser);
        UpdateClassroomRequest request = UpdateClassroomRequest.builder()
                .name("Lập trình Mạng V2")
                .version(99)
                .build();

        when(classroomAccessService.requireTeacherInClass(100L, 1L)).thenReturn(ownerMembership);
        when(classroomAccessService.requireClassroomExists(100L)).thenReturn(openClassroom);

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomService.updateClassroom(100L, request, null)
        );
        assertEquals(ErrorCode.VERSION_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("addCoTeacher rejects non-owner with 403 FORBIDDEN")
    void addCoTeacher_NonOwner_ThrowsForbidden() {
        authenticateAs(coTeacherUser);
        doThrow(new AppException(ErrorCode.FORBIDDEN))
                .when(classroomAccessService)
                .requireOwnerTeacher(100L, 2L);

        AppException ex = assertThrows(
                AppException.class,
                () -> classroomService.addCoTeacher(100L, new AddCoTeacherRequest(3L))
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
