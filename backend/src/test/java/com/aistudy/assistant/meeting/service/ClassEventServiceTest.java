package com.aistudy.assistant.meeting.service;

import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.service.ClassroomAccessService;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.meeting.dto.ClassEventResponse;
import com.aistudy.assistant.meeting.dto.CreateClassEventRequest;
import com.aistudy.assistant.meeting.dto.UpdateClassEventRequest;
import com.aistudy.assistant.meeting.mapper.ClassEventMapper;
import com.aistudy.assistant.meeting.service.impl.ClassEventServiceImpl;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.EventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClassEventServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ClassroomAccessService classroomAccessService;
    @Mock
    private NotificationOutboxService notificationOutboxService;

    @Spy
    private ClassEventMapper classEventMapper = new ClassEventMapper();

    @InjectMocks
    private ClassEventServiceImpl classEventService;

    private User teacherUser;
    private User studentUser1;
    private User studentUser2;
    private Classroom classroom;
    private ClassMember teacherMember;
    private ClassMember studentMember1;
    private ClassMember studentMember2;

    @BeforeEach
    void setUp() {
        teacherUser = User.builder()
                .id(2L)
                .email("gv@actvn.edu.vn")
                .fullName("TS. Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        studentUser1 = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        studentUser2 = User.builder()
                .id(12L)
                .email("sv02@actvn.edu.vn")
                .fullName("Lê Thị C")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        classroom = Classroom.builder()
                .id(10L)
                .code("KMA7N9P4")
                .name("Lập trình Mạng - K19A")
                .status(ClassroomStatus.OPEN)
                .owner(teacherUser)
                .build();

        teacherMember = ClassMember.builder()
                .id(100L)
                .classroom(classroom)
                .user(teacherUser)
                .classRole(ClassRole.OWNER_TEACHER)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        studentMember1 = ClassMember.builder()
                .id(101L)
                .classroom(classroom)
                .user(studentUser1)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        studentMember2 = ClassMember.builder()
                .id(102L)
                .classroom(classroom)
                .user(studentUser2)
                .classRole(ClassRole.STUDENT)
                .status(ClassMemberStatus.ACTIVE)
                .build();

        authenticateAs(teacherUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("createClassEvent enqueues CLASS_EVENT_CREATED into notification_outbox for all active students")
    void createClassEvent_Teacher_EnqueuesOutboxForAllActiveStudents() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
        CreateClassEventRequest request = CreateClassEventRequest.builder()
                .type(EventType.MEETING)
                .title("Họp giải đáp đồ án")
                .description("Thảo luận Socket TCP")
                .startAt(startAt)
                .endAt(endAt)
                .timezone("Asia/Ho_Chi_Minh")
                .meetingUrl("https://meet.google.com/abc-defg-hij")
                .build();

        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);
        when(classroomAccessService.requireClassroomExists(10L)).thenReturn(classroom);
        when(userRepository.getReferenceById(2L)).thenReturn(teacherUser);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event saved = inv.getArgument(0);
            saved.setId(1001L);
            saved.setVersion(0);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(10L, List.of(ClassRole.STUDENT)))
                .thenReturn(List.of(studentMember1, studentMember2));

        ClassEventResponse response = classEventService.createClassEvent(10L, request);

        assertEquals(1001L, response.id());
        assertEquals(10L, response.classId());
        assertEquals(EventType.MEETING, response.type());
        assertEquals("https://meet.google.com/abc-defg-hij", response.meetingUrl());
        assertEquals("Asia/Ho_Chi_Minh", response.timezone());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser1),
                eq("CLASS_EVENT_CREATED"),
                eq("EVENT"),
                eq(1001L),
                eq(""),
                payloadCaptor.capture()
        );
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser2),
                eq("CLASS_EVENT_CREATED"),
                eq("EVENT"),
                eq(1001L),
                eq(""),
                anyMap()
        );
        assertEquals("Lập trình Mạng - K19A", payloadCaptor.getValue().get("className"));
        assertEquals(1001L, payloadCaptor.getValue().get("eventId"));
    }

    @Test
    @DisplayName("createClassEvent with endAt <= startAt throws 400 VALIDATION_ERROR")
    void createClassEvent_EndBeforeOrEqualStart_ThrowsValidationError() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        CreateClassEventRequest request = CreateClassEventRequest.builder()
                .type(EventType.CLASS)
                .title("Buổi học không hợp lệ")
                .startAt(startAt)
                .endAt(startAt)
                .build();

        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);
        when(classroomAccessService.requireClassroomExists(10L)).thenReturn(classroom);

        AppException ex = assertThrows(AppException.class, () -> classEventService.createClassEvent(10L, request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
        verify(notificationOutboxService, never()).enqueueForAllChannels(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("createClassEvent with PERSONAL type throws 400 VALIDATION_ERROR")
    void createClassEvent_PersonalType_ThrowsValidationError() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        CreateClassEventRequest request = CreateClassEventRequest.builder()
                .type(EventType.PERSONAL)
                .title("Không hợp lệ")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .build();

        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);
        when(classroomAccessService.requireClassroomExists(10L)).thenReturn(classroom);

        AppException ex = assertThrows(AppException.class, () -> classEventService.createClassEvent(10L, request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createClassEvent with invalid meetingUrl throws 400 VALIDATION_ERROR")
    void createClassEvent_InvalidMeetingUrl_ThrowsValidationError() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        CreateClassEventRequest request = CreateClassEventRequest.builder()
                .type(EventType.MEETING)
                .title("Họp lớp")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .meetingUrl("ftp://invalid-meeting-url")
                .build();

        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);
        when(classroomAccessService.requireClassroomExists(10L)).thenReturn(classroom);

        AppException ex = assertThrows(AppException.class, () -> classEventService.createClassEvent(10L, request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createClassEvent by student throws 403 FORBIDDEN")
    void createClassEvent_Student_ThrowsForbidden() {
        authenticateAs(studentUser1);
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        CreateClassEventRequest request = CreateClassEventRequest.builder()
                .type(EventType.CLASS)
                .title("Sinh viên tạo trái phép")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .build();

        doThrow(new AppException(ErrorCode.FORBIDDEN))
                .when(classroomAccessService).requireTeacherInClass(10L, 11L);

        AppException ex = assertThrows(AppException.class, () -> classEventService.createClassEvent(10L, request));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateClassEvent with mismatched version throws 409 VERSION_CONFLICT")
    void updateClassEvent_VersionMismatch_ThrowsVersionConflict() {
        Instant startAt = Instant.now().plus(3, ChronoUnit.HOURS);
        Event existing = Event.builder()
                .id(1001L)
                .owner(teacherUser)
                .classroom(classroom)
                .type(EventType.MEETING)
                .title("Họp cũ")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(2)
                .build();

        UpdateClassEventRequest request = UpdateClassEventRequest.builder()
                .type(EventType.MEETING)
                .title("Họp mới")
                .startAt(startAt.plus(1, ChronoUnit.HOURS))
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .version(1)
                .build();

        when(eventRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(existing));
        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);

        AppException ex = assertThrows(
                AppException.class,
                () -> classEventService.updateClassEvent(1001L, request, null)
        );
        assertEquals(ErrorCode.VERSION_CONFLICT, ex.getErrorCode());
        verify(eventRepository, never()).saveAndFlush(any());
        verify(notificationOutboxService, never()).enqueueForAllChannels(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cancelClassEvent sets CANCELLED status and enqueues CLASS_EVENT_CANCELLED outbox notifications")
    void cancelClassEvent_Teacher_SetsCancelledAndEnqueuesOutbox() {
        Instant startAt = Instant.now().plus(4, ChronoUnit.HOURS);
        Event existing = Event.builder()
                .id(1001L)
                .owner(teacherUser)
                .classroom(classroom)
                .type(EventType.CLASS)
                .title("Buổi học bù")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        when(eventRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(existing));
        when(classroomAccessService.requireTeacherInClass(10L, 2L)).thenReturn(teacherMember);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.findActiveWithUserByClassIdAndRoles(10L, List.of(ClassRole.STUDENT)))
                .thenReturn(List.of(studentMember1, studentMember2));

        classEventService.cancelClassEvent(1001L);

        assertEquals(EventStatus.CANCELLED, existing.getStatus());
        verify(eventRepository).save(existing);
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser1),
                eq("CLASS_EVENT_CANCELLED"),
                eq("EVENT"),
                eq(1001L),
                eq(""),
                anyMap()
        );
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(studentUser2),
                eq("CLASS_EVENT_CANCELLED"),
                eq("EVENT"),
                eq(1001L),
                eq(""),
                anyMap()
        );
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
