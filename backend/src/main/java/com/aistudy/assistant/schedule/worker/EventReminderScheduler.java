package com.aistudy.assistant.schedule.worker;

import com.aistudy.assistant.classroom.entity.ClassMember;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Runs every 10 minutes to enqueue deduplicated {@code EVENT_30M} reminders into {@code notification_outbox}
 * for upcoming {@code CLASS}, {@code TEST}, and {@code MEETING} events starting within 30 minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    public static final String REMINDER_KEY_EVENT_30M = "EVENT_30M";
    public static final String EVENT_TYPE_REMINDER = "EVENT_REMINDER_30M";
    private static final Duration REMINDER_WINDOW = Duration.ofMinutes(30);
    private static final Set<EventType> REMINDER_TYPES = EnumSet.of(
            EventType.CLASS,
            EventType.TEST,
            EventType.MEETING
    );

    private final EventRepository eventRepository;
    private final ClassMemberRepository classMemberRepository;
    private final NotificationOutboxService notificationOutboxService;

    @Scheduled(fixedDelayString = "${app.schedule.reminder-poll-ms:600000}")
    @Transactional
    public void scheduleUpcomingEventReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(REMINDER_WINDOW);

        List<Event> upcomingEvents = eventRepository.findUpcomingEventsForReminder(REMINDER_TYPES, now, windowEnd);
        if (upcomingEvents.isEmpty()) {
            return;
        }

        Set<Long> classIds = upcomingEvents.stream()
                .filter(event -> event.getClassroom() != null)
                .map(event -> event.getClassroom().getId())
                .collect(Collectors.toSet());

        Map<Long, List<ClassMember>> membersByClassId = classIds.isEmpty()
                ? Map.of()
                : classMemberRepository.findActiveWithUserByClassIds(classIds).stream()
                        .collect(Collectors.groupingBy(member -> member.getClassroom().getId()));

        for (Event event : upcomingEvents) {
            List<User> recipients = resolveRecipients(event, membersByClassId);
            if (recipients.isEmpty()) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", event.getId());
            payload.put("type", event.getType().name());
            payload.put("title", event.getTitle());
            payload.put("startAt", event.getStartAt().toString());
            payload.put("endAt", event.getEndAt().toString());
            payload.put("timezone", event.getTimezone());
            if (event.getClassroom() != null) {
                payload.put("classId", event.getClassroom().getId());
                payload.put("className", event.getClassroom().getName());
            }
            if (event.getMeetingUrl() != null) {
                payload.put("meetingUrl", event.getMeetingUrl());
            }

            for (User recipient : recipients) {
                notificationOutboxService.enqueueForAllChannels(
                        recipient,
                        EVENT_TYPE_REMINDER,
                        "EVENT",
                        event.getId(),
                        REMINDER_KEY_EVENT_30M,
                        payload
                );
            }
        }

        log.debug("Processed 30m event reminders for {} upcoming events", upcomingEvents.size());
    }

    private List<User> resolveRecipients(Event event, Map<Long, List<ClassMember>> membersByClassId) {
        if (event.getClassroom() != null) {
            return membersByClassId.getOrDefault(event.getClassroom().getId(), List.of()).stream()
                    .map(ClassMember::getUser)
                    .toList();
        }
        return event.getOwner() != null ? List.of(event.getOwner()) : List.of();
    }
}
