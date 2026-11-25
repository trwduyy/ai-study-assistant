package com.aistudy.assistant.assignment.service.impl;

import com.aistudy.assistant.assignment.dto.TeacherDashboardResponse;
import com.aistudy.assistant.assignment.dto.TeacherDashboardResponse.RecentTestResultItem;
import com.aistudy.assistant.assignment.dto.TeacherDashboardResponse.UpcomingAssignmentItem;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.assignment.repository.AssignmentRepository.TeacherUpcomingAssignmentProjection;
import com.aistudy.assistant.assignment.repository.ClassTestRepository;
import com.aistudy.assistant.assignment.repository.ClassTestRepository.TeacherRecentTestProjection;
import com.aistudy.assistant.assignment.service.TeacherDashboardService;
import com.aistudy.assistant.classroom.repository.ClassJoinRequestRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

/**
 * Aggregates teacher dashboard metrics (FR-20) across active taught classes without N+1 queries.
 */
@Service
@RequiredArgsConstructor
public class TeacherDashboardServiceImpl implements TeacherDashboardService {

    private static final int DASHBOARD_LIMIT = 5;

    private final ClassMemberRepository classMemberRepository;
    private final ClassJoinRequestRepository classJoinRequestRepository;
    private final AssignmentRepository assignmentRepository;
    private final ClassTestRepository classTestRepository;
    private final NotificationRepository notificationRepository;

    @Override
    @Transactional(readOnly = true)
    public TeacherDashboardResponse getTeacherDashboard() {
        Long teacherId = SecurityUtils.currentUserId();
        Instant now = Instant.now();

        long totalClasses = classMemberRepository.countActiveClassesForTeacher(teacherId);
        long pendingJoinRequests = classJoinRequestRepository.countPendingForTeacher(teacherId);

        List<UpcomingAssignmentItem> upcomingAssignments = assignmentRepository
                .findUpcomingForTeacherDashboard(teacherId, now, PageRequest.of(0, DASHBOARD_LIMIT))
                .stream()
                .map(this::toUpcomingAssignmentItem)
                .toList();

        List<RecentTestResultItem> recentTestResults = classTestRepository
                .findRecentForTeacherDashboard(teacherId, PageRequest.of(0, DASHBOARD_LIMIT))
                .stream()
                .map(this::toRecentTestResultItem)
                .toList();

        long unreadNotificationCount = notificationRepository.countByUserIdAndReadAtIsNull(teacherId);

        return new TeacherDashboardResponse(
                totalClasses,
                pendingJoinRequests,
                upcomingAssignments,
                recentTestResults,
                unreadNotificationCount
        );
    }

    private UpcomingAssignmentItem toUpcomingAssignmentItem(TeacherUpcomingAssignmentProjection row) {
        long submitted = row.getSubmittedCount() != null ? row.getSubmittedCount() : 0L;
        long total = row.getTotalStudents() != null ? row.getTotalStudents() : 0L;
        double rate = total > 0 ? roundTwoDecimals((submitted * 100.0) / total) : 0.0;
        return new UpcomingAssignmentItem(
                row.getAssignmentId(),
                row.getClassId(),
                row.getClassName(),
                row.getTitle(),
                row.getMaxScore(),
                row.getDueAt(),
                submitted,
                total,
                rate
        );
    }

    private RecentTestResultItem toRecentTestResultItem(TeacherRecentTestProjection row) {
        long submitted = row.getSubmittedStudents() != null ? row.getSubmittedStudents() : 0L;
        long total = row.getTotalStudents() != null ? row.getTotalStudents() : 0L;
        double avgScore = row.getAverageScore() != null ? roundTwoDecimals(row.getAverageScore()) : 0.0;
        return new RecentTestResultItem(
                row.getTestId(),
                row.getClassId(),
                row.getClassName(),
                row.getTitle(),
                row.getStartAt(),
                row.getEndAt(),
                submitted,
                total,
                avgScore
        );
    }

    private double roundTwoDecimals(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
