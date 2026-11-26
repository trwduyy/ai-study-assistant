package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import com.aistudy.assistant.schedule.entity.TaskStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record StudentDashboardResponse(
        DashboardOverviewSummary overview,
        List<RecentAttemptSummary> recentAttempts,
        List<DashboardReviewTopicSummary> reviewTopics,
        List<UpcomingEventSummary> upcomingEvents,
        List<DueAssignmentSummary> dueAssignments,
        long unreadNotificationCount,
        List<TodayTaskSummary> todayTasks,
        List<ActiveGoalSummary> activeGoals,
        long dueFlashcardsCount
) {
    public record DashboardOverviewSummary(
            long totalCourses,
            long totalMaterials,
            double averageScore,
            double accuracyRate
    ) {
    }

    public record RecentAttemptSummary(
            Long attemptId,
            Long quizId,
            String quizTitle,
            double score,
            double maxScore,
            Instant submittedAt
    ) {
    }

    public record DashboardReviewTopicSummary(
            Long topicId,
            String topicTitle,
            String confidence,
            String reason
    ) {
    }

    public record UpcomingEventSummary(
            Long eventId,
            Long classId,
            String className,
            EventType type,
            String title,
            Instant startAt,
            Instant endAt,
            String timezone,
            EventStatus status,
            String meetingUrl,
            EventSource source
    ) {
    }

    public record DueAssignmentSummary(
            Long assignmentId,
            Long classId,
            String className,
            String title,
            Instant dueAt,
            Boolean allowLate,
            BigDecimal maxScore
    ) {
    }

    public record TodayTaskSummary(
            Long taskId,
            Long goalId,
            String title,
            LocalDate taskDate,
            GoalPriority priority,
            TaskStatus status,
            Instant remindAt
    ) {
    }

    public record ActiveGoalSummary(
            Long goalId,
            String title,
            String subject,
            LocalDate dueDate,
            GoalPriority priority,
            GoalStatus status
    ) {
    }
}
