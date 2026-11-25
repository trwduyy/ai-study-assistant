package com.aistudy.assistant.schedule.worker;

import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.EventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReminderSchedulerTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private NotificationOutboxService notificationOutboxService;

    @InjectMocks
    private EventReminderScheduler eventReminderScheduler;

    @Test
    @DisplayName("scheduleUpcomingEventReminders enqueues EVENT_30M outbox reminders for class members")
    void scheduleUpcomingEventReminders_EnqueuesDeduplicatedOutbox() {
        User teacher = User.builder().id(2L).email("gv@actvn.edu.vn").role(UserRole.TEACHER).status(UserStatus.ACTIVE).build();
        User student = User.builder().id(11L).email("sv@actvn.edu.vn").role(UserRole.STUDENT).status(UserStatus.ACTIVE).build();
        Classroom classroom = Classroom.builder().id(10L).code("KMA7N9P4").name("Lập trình Mạng").status(ClassroomStatus.OPEN).owner(teacher).build();

        Instant startAt = Instant.now().plus(20, ChronoUnit.MINUTES);
        Event classMeeting = Event.builder()
                .id(1001L)
                .owner(teacher)
                .classroom(classroom)
                .type(EventType.MEETING)
                .title("Họp đồ án")
                .startAt(startAt)
                .endAt(startAt.plus(1, ChronoUnit.HOURS))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .build();

        ClassMember teacherMember = ClassMember.builder()
                .id(1L).classroom(classroom).user(teacher).classRole(ClassRole.OWNER_TEACHER).status(ClassMemberStatus.ACTIVE).build();
        ClassMember studentMember = ClassMember.builder()
                .id(2L).classroom(classroom).user(student).classRole(ClassRole.STUDENT).status(ClassMemberStatus.ACTIVE).build();

        when(eventRepository.findUpcomingEventsForReminder(any(), any(), any())).thenReturn(List.of(classMeeting));
        when(classMemberRepository.findActiveWithUserByClassIds(Set.of(10L)))
                .thenReturn(List.of(teacherMember, studentMember));

        eventReminderScheduler.scheduleUpcomingEventReminders();

        verify(notificationOutboxService).enqueueForAllChannels(
                eq(teacher),
                eq(EventReminderScheduler.EVENT_TYPE_REMINDER),
                eq("EVENT"),
                eq(1001L),
                eq(EventReminderScheduler.REMINDER_KEY_EVENT_30M),
                anyMap()
        );
        verify(notificationOutboxService).enqueueForAllChannels(
                eq(student),
                eq(EventReminderScheduler.EVENT_TYPE_REMINDER),
                eq("EVENT"),
                eq(1001L),
                eq(EventReminderScheduler.REMINDER_KEY_EVENT_30M),
                anyMap()
        );
    }

    @Test
    @DisplayName("scheduleUpcomingEventReminders skips member lookup when no events start within 30 minutes")
    void scheduleUpcomingEventReminders_Empty_DoesNotQueryMembers() {
        when(eventRepository.findUpcomingEventsForReminder(any(), any(), any())).thenReturn(List.of());

        eventReminderScheduler.scheduleUpcomingEventReminders();

        verify(classMemberRepository, never()).findActiveWithUserByClassIds(any());
        verify(notificationOutboxService, never()).enqueueForAllChannels(any(), any(), any(), any(), any(), any());
    }
}
