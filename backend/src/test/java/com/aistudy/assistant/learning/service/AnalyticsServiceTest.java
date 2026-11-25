package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.assignment.repository.AssignmentRepository;
import com.aistudy.assistant.classroom.entity.ClassRole;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.dto.AnalyticsOverviewResponse;
import com.aistudy.assistant.learning.dto.ReviewTopicResponse;
import com.aistudy.assistant.learning.dto.StudentDashboardResponse;
import com.aistudy.assistant.learning.entity.AttemptStatus;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.flashcard.repository.FlashcardRepository;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository;
import com.aistudy.assistant.learning.repository.AttemptAnswerRepository.TopicPerformanceProjection;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository;
import com.aistudy.assistant.learning.repository.QuizAttemptRepository.OverviewStatsProjection;
import com.aistudy.assistant.notification.repository.NotificationRepository;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import com.aistudy.assistant.schedule.entity.Task;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import com.aistudy.assistant.schedule.repository.TaskRepository;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private QuizAttemptRepository quizAttemptRepository;
    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;
    @Mock
    private AssignmentRepository assignmentRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private FlashcardRepository flashcardRepository;

    @InjectMocks
    private AnalyticsServiceImpl analyticsService;

    @BeforeEach
    void setUp() {
        User student = User.builder()
                .id(10L)
                .email("student@aistudy.local")
                .passwordHash("hash")
                .fullName("Sinh Viên")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        UserPrincipal principal = UserPrincipal.create(student);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getOverview and getStudentDashboard aggregate metrics including 7-day upcoming events")
    void getOverviewAndDashboard_AggregateCleanly() {
        OverviewStatsProjection stats = mock(OverviewStatsProjection.class);
        when(stats.getTotalAttempts()).thenReturn(4L);
        when(stats.getAverageScore()).thenReturn(new BigDecimal("7.75"));
        when(stats.getTotalQuestionsAttempted()).thenReturn(40L);
        when(stats.getTotalCorrectAnswers()).thenReturn(31L);

        when(courseRepository.countByOwnerIdAndDeletedAtIsNull(10L)).thenReturn(3L);
        when(materialRepository.countByOwnerIdAndDeletedAtIsNull(10L)).thenReturn(9L);
        when(quizAttemptRepository.aggregateOverviewStats(10L, AttemptStatus.SUBMITTED)).thenReturn(stats);
        when(quizAttemptRepository.findRecentSubmittedAttempts(10L, AttemptStatus.SUBMITTED, PageRequest.of(0, 5)))
                .thenReturn(List.of(new StudentDashboardResponse.RecentAttemptSummary(
                        901L, 601L, "Quiz TCP", 8.0, 10.0, Instant.now()
                )));
        when(attemptAnswerRepository.findTopicPerformance(10L, AttemptStatus.SUBMITTED)).thenReturn(List.of());

        ActiveClassRoleProjection roleProj = mock(ActiveClassRoleProjection.class);
        when(roleProj.getClassId()).thenReturn(100L);
        when(classMemberRepository.findActiveClassRolesByUserId(10L)).thenReturn(List.of(roleProj));

        Classroom classroom = Classroom.builder().id(100L).name("Lập trình Mạng").build();
        Event upcomingEvent = Event.builder()
                .id(1001L)
                .classroom(classroom)
                .type(EventType.MEETING)
                .title("Họp đồ án")
                .startAt(Instant.now().plusSeconds(7200))
                .endAt(Instant.now().plusSeconds(10800))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .meetingUrl("https://meet.google.com/abc-defg-hij")
                .source(EventSource.MANUAL)
                .build();
        when(eventRepository.findUpcomingCalendarEventsForDashboard(
                eq(10L), eq(List.of(100L)), any(Instant.class), any(Instant.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(upcomingEvent));

        when(assignmentRepository.findUpcomingUnsubmittedForStudent(eq(10L), any(Instant.class), eq(PageRequest.of(0, 5))))
                .thenReturn(List.of(new StudentDashboardResponse.DueAssignmentSummary(
                        401L, 100L, "Lập trình Mạng", "Socket TCP", Instant.now().plusSeconds(3600), true, new BigDecimal("10.00")
                )));
        when(notificationRepository.countByUserIdAndReadAtIsNull(10L)).thenReturn(3L);
        Goal activeGoal = Goal.builder()
                .id(51L)
                .title("Đạt 9.0 Mạng máy tính")
                .subject("Mạng máy tính")
                .dueDate(LocalDate.of(2026, 12, 20))
                .priority(GoalPriority.HIGH)
                .status(GoalStatus.ACTIVE)
                .build();
        Task todayTask = Task.builder()
                .id(401L)
                .goal(activeGoal)
                .title("Ôn tập TCP")
                .taskDate(LocalDate.now(ZoneOffset.UTC))
                .priority(GoalPriority.HIGH)
                .status(TaskStatus.TODO)
                .build();
        when(taskRepository.findTodayTasksForDashboard(eq(10L), any(LocalDate.class), eq(PageRequest.of(0, 10))))
                .thenReturn(List.of(todayTask));
        when(goalRepository.findTop5ByUserIdAndStatusOrderByDueDateAscIdAsc(10L, GoalStatus.ACTIVE))
                .thenReturn(List.of(activeGoal));
        when(flashcardRepository.countDueCardsByUserId(eq(10L), any(Instant.class))).thenReturn(7L);

        AnalyticsOverviewResponse overview = analyticsService.getOverview();
        StudentDashboardResponse dashboard = analyticsService.getStudentDashboard();

        assertEquals(3L, overview.totalCourses());
        assertEquals(9L, overview.totalMaterials());
        assertEquals(77.5, overview.accuracyRate());
        assertEquals(1, dashboard.recentAttempts().size());
        assertEquals(1, dashboard.upcomingEvents().size());
        assertEquals(1001L, dashboard.upcomingEvents().get(0).eventId());
        assertEquals(1, dashboard.dueAssignments().size());
        assertEquals(401L, dashboard.dueAssignments().get(0).assignmentId());
        assertEquals(3L, dashboard.unreadNotificationCount());
        assertEquals(1, dashboard.todayTasks().size());
        assertEquals(401L, dashboard.todayTasks().get(0).taskId());
        assertEquals(1, dashboard.activeGoals().size());
        assertEquals(51L, dashboard.activeGoals().get(0).goalId());
        assertEquals(7L, dashboard.dueFlashcardsCount());
    }

    @Test
    @DisplayName("getReviewTopics reports objective accuracy and sample confidence without learner ability labels")
    void getReviewTopics_IncludesConfidenceAndReason() {
        TopicPerformanceProjection row = mock(TopicPerformanceProjection.class);
        when(row.getCourseId()).thenReturn(101L);
        when(row.getCourseName()).thenReturn("Mạng máy tính");
        when(row.getTopicId()).thenReturn(301L);
        when(row.getTopicTitle()).thenReturn("Kiểm soát tắc nghẽn TCP");
        when(row.getSampleSize()).thenReturn(12L);
        when(row.getCorrectCount()).thenReturn(6L);

        Topic topic = Topic.builder().id(301L).title("Kiểm soát tắc nghẽn TCP").build();
        Material material = Material.builder()
                .id(401L)
                .topic(topic)
                .fileName("Chuong-3-TCP.pdf")
                .status(MaterialStatus.EXTRACTED)
                .build();

        when(attemptAnswerRepository.findTopicPerformance(10L, AttemptStatus.SUBMITTED)).thenReturn(List.of(row));
        when(materialRepository.findByTopicIdInAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                List.of(301L), MaterialStatus.EXTRACTED))
                .thenReturn(List.of(material));

        List<ReviewTopicResponse> topics = analyticsService.getReviewTopics();

        assertEquals(1, topics.size());
        assertEquals("HIGH", topics.get(0).confidence());
        assertEquals(50.0, topics.get(0).accuracyRate());
        assertEquals(401L, topics.get(0).suggestedMaterialId());
        assertTrue(topics.get(0).reason().contains("50.0%"));
        assertTrue(topics.get(0).reason().contains("12"));
    }
}
