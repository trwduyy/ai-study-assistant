package com.aistudy.assistant.assignment.service;

import com.aistudy.assistant.assignment.dto.TeacherDashboardResponse;
import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.assignment.repository.AssignmentRepository.TeacherUpcomingAssignmentProjection;
import com.aistudy.assistant.assignment.repository.ClassTestRepository;
import com.aistudy.assistant.assignment.repository.ClassTestRepository.TeacherRecentTestProjection;
import com.aistudy.assistant.assignment.service.impl.TeacherDashboardServiceImpl;
import com.aistudy.assistant.classroom.repository.ClassJoinRequestRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherDashboardServiceTest {

    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private ClassJoinRequestRepository classJoinRequestRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private ClassTestRepository classTestRepository;
    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private TeacherDashboardServiceImpl teacherDashboardService;

    @BeforeEach
    void setUp() {
        User teacher = User.builder()
                .id(1L)
                .email("gv@actvn.edu.vn")
                .fullName("TS. Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.TEACHER)
                .status(UserStatus.ACTIVE)
                .build();
        UserPrincipal principal = UserPrincipal.create(teacher);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getTeacherDashboard aggregates classes, pending requests, upcoming assignments, and tests")
    void getTeacherDashboard_AggregatesMetricsCleanly() {
        when(classMemberRepository.countActiveClassesForTeacher(1L)).thenReturn(3L);
        when(classJoinRequestRepository.countPendingForTeacher(1L)).thenReturn(5L);

        TeacherUpcomingAssignmentProjection assignmentRow = mock(TeacherUpcomingAssignmentProjection.class);
        when(assignmentRow.getAssignmentId()).thenReturn(401L);
        when(assignmentRow.getClassId()).thenReturn(10L);
        when(assignmentRow.getClassName()).thenReturn("Lập trình Mạng");
        when(assignmentRow.getTitle()).thenReturn("Socket TCP");
        when(assignmentRow.getMaxScore()).thenReturn(new BigDecimal("10.00"));
        when(assignmentRow.getDueAt()).thenReturn(Instant.now().plus(2, ChronoUnit.DAYS));
        when(assignmentRow.getSubmittedCount()).thenReturn(15L);
        when(assignmentRow.getTotalStudents()).thenReturn(40L);

        when(assignmentRepository.findUpcomingForTeacherDashboard(eq(1L), any(Instant.class), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(assignmentRow));

        TeacherRecentTestProjection testRow = mock(TeacherRecentTestProjection.class);
        when(testRow.getTestId()).thenReturn(701L);
        when(testRow.getClassId()).thenReturn(10L);
        when(testRow.getClassName()).thenReturn("Lập trình Mạng");
        when(testRow.getTitle()).thenReturn("Kiểm tra 15 phút");
        when(testRow.getStartAt()).thenReturn(Instant.now().minus(1, ChronoUnit.DAYS));
        when(testRow.getEndAt()).thenReturn(Instant.now().minus(20, ChronoUnit.HOURS));
        when(testRow.getSubmittedStudents()).thenReturn(36L);
        when(testRow.getTotalStudents()).thenReturn(40L);
        when(testRow.getAverageScore()).thenReturn(8.125);

        when(classTestRepository.findRecentForTeacherDashboard(1L, PageRequest.of(0, 5)))
                .thenReturn(List.of(testRow));
        when(notificationRepository.countByUserIdAndReadAtIsNull(1L)).thenReturn(4L);

        TeacherDashboardResponse dashboard = teacherDashboardService.getTeacherDashboard();

        assertEquals(3L, dashboard.totalClasses());
        assertEquals(5L, dashboard.pendingJoinRequests());
        assertEquals(1, dashboard.upcomingAssignments().size());
        assertEquals(37.5, dashboard.upcomingAssignments().get(0).submissionRate());
        assertEquals(1, dashboard.recentTestResults().size());
        assertEquals(8.13, dashboard.recentTestResults().get(0).averageScore());
        assertEquals(4L, dashboard.unreadNotificationCount());
    }
}
