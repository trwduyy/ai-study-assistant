package com.aistudy.assistant.notification.worker;

import com.aistudy.assistant.assignment.entity.Assignment;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.notification.service.NotificationOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodically scans assignments due within the next 24 hours and enqueues deduplicated
 * {@code DUE_24H} reminders into {@code notification_outbox} for active students who have not submitted.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    public static final String REMINDER_KEY_DUE_24H = "DUE_24H";
    private static final Duration REMINDER_WINDOW = Duration.ofHours(24);

    private final AssignmentRepository assignmentRepository;
    private final NotificationOutboxService notificationOutboxService;

    @Scheduled(fixedDelayString = "${app.notification.reminder-poll-ms:900000}")
    @Transactional
    public void scheduleUpcomingDeadlineReminders() {
        Instant now = Instant.now();
        Instant windowEnd = now.plus(REMINDER_WINDOW);

        List<Assignment> dueSoonAssignments = assignmentRepository.findDueWithinWindow(now, windowEnd);
        for (Assignment assignment : dueSoonAssignments) {
            List<Long> pendingStudentIds = assignmentRepository.findActiveStudentIdsWithoutSubmission(
                    assignment.getId(),
                    assignment.getClassroom().getId()
            );
            if (pendingStudentIds.isEmpty()) {
                continue;
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("assignmentId", assignment.getId());
            payload.put("title", assignment.getTitle());
            payload.put("classId", assignment.getClassroom().getId());
            payload.put("className", assignment.getClassroom().getName());
            payload.put("dueAt", assignment.getDueAt().toString());

            for (Long studentId : pendingStudentIds) {
                notificationOutboxService.enqueue(
                        studentId,
                        "ASSIGNMENT_DUE_SOON",
                        "ASSIGNMENT",
                        assignment.getId(),
                        REMINDER_KEY_DUE_24H,
                        payload
                );
            }
        }
        if (!dueSoonAssignments.isEmpty()) {
            log.debug("Processed 24h deadline reminders for {} upcoming assignments", dueSoonAssignments.size());
        }
    }
}
