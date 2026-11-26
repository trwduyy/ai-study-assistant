package com.aistudy.assistant.schedule.worker;

import com.aistudy.assistant.notification.service.NotificationOutboxService;
import com.aistudy.assistant.schedule.entity.Task;
import com.aistudy.assistant.schedule.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Periodic scheduler dispatching deduplicated {@code TASK_REMIND_{id}} notifications for due task reminders
 * and marking overdue {@code TODO} tasks from past UTC dates as {@code MISSED}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskReminderScheduler {

    public static final String EVENT_TYPE_TASK_REMINDER = "TASK_REMINDER";
    public static final String REMINDER_KEY_PREFIX = "TASK_REMIND_";
    private static final Duration REMINDER_LOOKBACK_WINDOW = Duration.ofMinutes(15);

    private final TaskRepository taskRepository;
    private final NotificationOutboxService notificationOutboxService;

    @Scheduled(fixedDelayString = "${app.schedule.task-reminder-poll-ms:600000}")
    @Transactional
    public void scheduleTaskReminders() {
        Instant now = Instant.now();
        Instant windowStart = now.minus(REMINDER_LOOKBACK_WINDOW);

        List<Task> dueTasks = taskRepository.findTasksDueForReminder(windowStart, now);
        for (Task task : dueTasks) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("taskId", task.getId());
            payload.put("title", task.getTitle());
            payload.put("taskDate", task.getTaskDate().toString());
            payload.put("remindAt", task.getRemindAt().toString());

            notificationOutboxService.enqueueForAllChannels(
                    task.getUser(),
                    EVENT_TYPE_TASK_REMINDER,
                    "TASK",
                    task.getId(),
                    REMINDER_KEY_PREFIX + task.getId(),
                    payload
            );
        }

        if (!dueTasks.isEmpty()) {
            log.debug("Processed task reminders for {} tasks", dueTasks.size());
        }
    }

    @Scheduled(cron = "${app.schedule.missed-tasks-cron:0 5 0 * * *}", zone = "UTC")
    @Transactional
    public void markMissedTasks() {
        Instant now = Instant.now();
        LocalDate todayUtc = LocalDate.now(ZoneOffset.UTC);
        int updated = taskRepository.markOverdueTasksAsMissed(todayUtc, now);
        if (updated > 0) {
            log.info("Marked {} overdue TODO tasks prior to {} UTC as MISSED", updated, todayUtc);
        }
    }
}
