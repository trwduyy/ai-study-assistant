package com.aistudy.assistant.learning.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record StudentDashboardResponse(
        DashboardOverviewSummary overview,
        List<RecentAttemptSummary> recentAttempts,
        List<DashboardReviewTopicSummary> reviewTopics,
        List<Object> upcomingEvents,
        List<DueAssignmentSummary> dueAssignments,
        long unreadNotificationCount,
        List<Object> todayTasks
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
}
