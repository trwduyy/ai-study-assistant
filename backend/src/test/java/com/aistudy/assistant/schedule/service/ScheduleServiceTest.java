package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.schedule.dto.CalendarEventResponse;
import com.aistudy.assistant.schedule.dto.CalendarView;
import com.aistudy.assistant.schedule.dto.CreatePersonalEventRequest;
import com.aistudy.assistant.schedule.dto.PersonalEventResponse;
import com.aistudy.assistant.schedule.dto.UpdatePersonalEventRequest;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.mapper.ScheduleMapper;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.service.impl.ScheduleServiceImpl;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private UserRepository userRepository;

    @Spy
    private ScheduleMapper scheduleMapper = new ScheduleMapper();

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private User studentUser;
    private User otherStudentUser;
    private User teacherUser;
    private Classroom classroom;

    @BeforeEach
    void setUp() {
        studentUser = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        otherStudentUser = User.builder()
                .id(12L)
                .email("sv02@actvn.edu.vn")
                .fullName("Lê Thị C")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        teacherUser = User.builder()
                .id(2L)
                .email("gv@actvn.edu.vn")
                .fullName("TS. Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();

        classroom = Classroom.builder()
                .id(10L)
                .code("KMA7N9P4")
                .name("Lập trình Mạng - K19A")
                .status(ClassroomStatus.OPEN)
                .owner(teacherUser)
                .build();

        authenticateAs(studentUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("updatePersonalEvent on a classroom event (classId != null) throws 403 FORBIDDEN")
    void updatePersonalEvent_ClassEvent_ThrowsForbidden() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Event classEvent = Event.builder()
                .id(1001L)
                .owner(teacherUser)
                .classroom(classroom)
                .type(EventType.CLASS)
                .title("Buổi học lớp")
                .startAt(startAt)
                .endAt(startAt.plus(2, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        UpdatePersonalEventRequest request = UpdatePersonalEventRequest.builder()
                .title("Cố tình sửa sự kiện lớp")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .version(0)
                .build();

        when(eventRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(classEvent));

        AppException ex = assertThrows(
                AppException.class,
                () -> scheduleService.updatePersonalEvent(1001L, request, null)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(eventRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("deletePersonalEvent on a classroom event (classId != null) throws 403 FORBIDDEN")
    void deletePersonalEvent_ClassEvent_ThrowsForbidden() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Event classEvent = Event.builder()
                .id(1001L)
                .owner(teacherUser)
                .classroom(classroom)
                .type(EventType.MEETING)
                .title("Họp lớp")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        when(eventRepository.findWithDetailsById(1001L)).thenReturn(Optional.of(classEvent));

        AppException ex = assertThrows(
                AppException.class,
                () -> scheduleService.deletePersonalEvent(1001L)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePersonalEvent on another user's personal event throws 403 FORBIDDEN")
    void updatePersonalEvent_OtherUserEvent_ThrowsForbidden() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Event otherPersonalEvent = Event.builder()
                .id(2002L)
                .owner(otherStudentUser)
                .classroom(null)
                .type(EventType.PERSONAL)
                .title("Lịch của bạn khác")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        UpdatePersonalEventRequest request = UpdatePersonalEventRequest.builder()
                .title("Sửa trộm")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .version(0)
                .build();

        when(eventRepository.findWithDetailsById(2002L)).thenReturn(Optional.of(otherPersonalEvent));

        AppException ex = assertThrows(
                AppException.class,
                () -> scheduleService.updatePersonalEvent(2002L, request, null)
        );
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("createPersonalEvent with endAt <= startAt throws 400 VALIDATION_ERROR")
    void createPersonalEvent_EndBeforeStart_ThrowsValidationError() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        CreatePersonalEventRequest request = CreatePersonalEventRequest.builder()
                .title("Tự học TCP")
                .startAt(startAt)
                .endAt(startAt.minus(10, ChronoUnit.MINUTES))
                .build();

        AppException ex = assertThrows(AppException.class, () -> scheduleService.createPersonalEvent(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPersonalEvent saves UTC event with timezone and returns PersonalEventResponse")
    void createPersonalEvent_Valid_Success() {
        Instant startAt = Instant.now().plus(2, ChronoUnit.HOURS);
        Instant endAt = startAt.plus(90, ChronoUnit.MINUTES);
        CreatePersonalEventRequest request = CreatePersonalEventRequest.builder()
                .title("Tự ôn tập chương 3")
                .description("Làm quiz TCP")
                .startAt(startAt)
                .endAt(endAt)
                .timezone("Asia/Ho_Chi_Minh")
                .build();

        when(userRepository.getReferenceById(11L)).thenReturn(studentUser);
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> {
            Event saved = inv.getArgument(0);
            saved.setId(2001L);
            saved.setVersion(0);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        PersonalEventResponse response = scheduleService.createPersonalEvent(request);

        assertEquals(2001L, response.id());
        assertEquals(EventType.PERSONAL, response.type());
        assertEquals("Asia/Ho_Chi_Minh", response.timezone());
        assertEquals(EventSource.MANUAL, response.source());
    }

    @Test
    @DisplayName("getCalendar merges personal, Google, and class events with proper readOnly flags")
    void getCalendar_Student_MergesAndSetsReadOnlyFlags() {
        Instant from = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant to = from.plus(7, ChronoUnit.DAYS);

        ActiveClassRoleProjection roleProj = mock(ActiveClassRoleProjection.class);
        when(roleProj.getClassId()).thenReturn(10L);
        when(roleProj.getClassRole()).thenReturn(ClassRole.STUDENT);
        when(classMemberRepository.findActiveClassRolesByUserId(11L)).thenReturn(List.of(roleProj));

        Event classEvent = Event.builder()
                .id(1001L)
                .owner(teacherUser)
                .classroom(classroom)
                .type(EventType.CLASS)
                .title("Buổi học lớp")
                .startAt(from.plus(2, ChronoUnit.HOURS))
                .endAt(from.plus(4, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        Event googleEvent = Event.builder()
                .id(2001L)
                .owner(studentUser)
                .classroom(null)
                .type(EventType.PERSONAL)
                .title("Google Calendar Event")
                .startAt(from.plus(5, ChronoUnit.HOURS))
                .endAt(from.plus(6, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.GOOGLE)
                .externalId("google-evt-1")
                .version(0)
                .build();

        Event personalEvent = Event.builder()
                .id(2002L)
                .owner(studentUser)
                .classroom(null)
                .type(EventType.PERSONAL)
                .title("Tự học tối")
                .startAt(from.plus(7, ChronoUnit.HOURS))
                .endAt(from.plus(8, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .version(0)
                .build();

        when(eventRepository.findCalendarEventsInRange(11L, Set.of(10L), from, to))
                .thenReturn(List.of(classEvent, googleEvent, personalEvent));

        List<CalendarEventResponse> calendar = scheduleService.getCalendar(from, to, CalendarView.WEEK);

        assertEquals(3, calendar.size());
        assertTrue(calendar.get(0).readOnly(), "Class event must be readOnly for student");
        assertTrue(calendar.get(1).readOnly(), "Google event must always be readOnly");
        assertFalse(calendar.get(2).readOnly(), "Manual personal event must be editable by owner");
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
