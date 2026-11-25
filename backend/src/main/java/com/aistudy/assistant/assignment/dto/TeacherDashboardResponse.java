package com.aistudy.assistant.assignment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Teacher dashboard summary (FR-20) across active taught classes.
 */
public record TeacherDashboardResponse(
        long totalClasses,
        long pendingJoinRequests,
        List<UpcomingAssignmentItem> upcomingAssignments,
        List<RecentTestResultItem> recentTestResults,
        long unreadNotificationCount
) {
    public record UpcomingAssignmentItem(
            Long assignmentId,
            Long classId,
            String className,
            String title,
            BigDecimal maxScore,
            Instant dueAt,
            long submittedCount,
            long totalStudents,
            double submissionRate
    ) {
    }

    public record RecentTestResultItem(
            Long testId,
            Long classId,
            String className,
            String title,
            Instant startAt,
            Instant endAt,
            long submittedStudents,
            long totalStudents,
            double averageScore
    ) {
    }
}
