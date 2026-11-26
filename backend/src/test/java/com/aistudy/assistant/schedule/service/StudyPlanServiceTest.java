package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.schedule.dto.DecideStudyPlanItemRequest;
import com.aistudy.assistant.schedule.dto.StudyPlanItemResponse;
import com.aistudy.assistant.schedule.dto.StudyPlanResponse;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.StudyPlan;
import com.aistudy.assistant.schedule.entity.StudyPlanItem;
import com.aistudy.assistant.schedule.entity.StudyPlanItemDecision;
import com.aistudy.assistant.schedule.entity.StudyPlanStatus;
import com.aistudy.assistant.schedule.entity.Task;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import com.aistudy.assistant.schedule.repository.StudyPlanRepository;
import com.aistudy.assistant.schedule.repository.TaskRepository;
import com.aistudy.assistant.schedule.service.impl.StudyPlanServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    @Mock
    private StudyPlanRepository studyPlanRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private GoalRepository goalRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;

    @InjectMocks
    private StudyPlanServiceImpl studyPlanService;

    private User student;

    @BeforeEach
    void setUp() {
        student = User.builder()
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
    @DisplayName("getStudyPlan returns conflict flags against existing calendar without mutating events (BR-11)")
    void getStudyPlan_FlagsConflictsWithoutMutatingEvents() {
        StudyPlan plan = buildTwoItemPlan();
        Event existingEvent = Event.builder()
                .id(77L)
                .owner(student)
                .type(EventType.PERSONAL)
                .title("Học nhóm")
                .startAt(Instant.parse("2026-11-26T12:45:00Z"))
                .endAt(Instant.parse("2026-11-26T13:15:00Z"))
                .status(EventStatus.UPCOMING)
                .source(EventSource.MANUAL)
                .build();

        when(studyPlanRepository.findWithItemsById(88L)).thenReturn(Optional.of(plan));
        when(classMemberRepository.findActiveClassRolesByUserId(10L)).thenReturn(List.of());
        when(eventRepository.findOverlappingOwnerEvents(eq(10L), any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(List.of(existingEvent));

        StudyPlanResponse response = studyPlanService.getStudyPlan(88L);

        assertEquals(StudyPlanStatus.PROPOSED, response.status());
        assertEquals(2, response.items().size());
        assertTrue(response.items().get(0).hasConflict());
        assertEquals(77L, response.items().get(0).conflictingEvents().get(0).eventId());
        assertFalse(response.items().get(1).hasConflict());
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidePlanItem ACCEPTED then REJECTED creates Task only for accepted item and closes plan")
    void decidePlanItem_AcceptedCreatesTaskAndTransitionsStatusToPartialThenClosed() {
        StudyPlan plan = buildTwoItemPlan();
        Goal goal = Goal.builder().id(51L).user(student).title("Đạt 9.0 Mạng").priority(GoalPriority.HIGH).build();

        when(studyPlanRepository.findWithItemsById(88L)).thenReturn(Optional.of(plan));
        when(goalRepository.findByIdAndUserId(51L, 10L)).thenReturn(Optional.of(goal));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            saved.setId(901L);
            return saved;
        });
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.findActiveClassRolesByUserId(10L)).thenReturn(List.of());
        when(eventRepository.findOverlappingOwnerEvents(eq(10L), any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(List.of());

        DecideStudyPlanItemRequest acceptReq = new DecideStudyPlanItemRequest();
        acceptReq.setDecision(StudyPlanItemDecision.ACCEPTED);

        StudyPlanItemResponse acceptedRes = studyPlanService.decidePlanItem(88L, 601L, acceptReq);
        assertEquals(StudyPlanItemDecision.ACCEPTED, acceptedRes.decision());
        assertEquals(901L, acceptedRes.taskId());
        assertEquals(StudyPlanStatus.PARTIAL, acceptedRes.planStatus());
        assertEquals(StudyPlanStatus.PARTIAL, plan.getStatus());

        DecideStudyPlanItemRequest rejectReq = new DecideStudyPlanItemRequest();
        rejectReq.setDecision(StudyPlanItemDecision.REJECTED);

        StudyPlanItemResponse rejectedRes = studyPlanService.decidePlanItem(88L, 602L, rejectReq);
        assertEquals(StudyPlanItemDecision.REJECTED, rejectedRes.decision());
        assertNull(rejectedRes.taskId());
        assertEquals(StudyPlanStatus.CLOSED, rejectedRes.planStatus());
        assertEquals(StudyPlanStatus.CLOSED, plan.getStatus());
        verify(taskRepository).save(any(Task.class));
        verify(eventRepository, never()).save(any());
    }

    @Test
    @DisplayName("decidePlanItem EDITED creates Task using edited title and schedule")
    void decidePlanItem_EditedCreatesTaskWithEditedFields() {
        StudyPlan plan = buildTwoItemPlan();
        when(studyPlanRepository.findWithItemsById(88L)).thenReturn(Optional.of(plan));
        when(goalRepository.findByIdAndUserId(51L, 10L)).thenReturn(Optional.empty());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            saved.setId(902L);
            assertEquals("Ôn Slow Start nâng cao", saved.getTitle());
            assertEquals(LocalDate.of(2026, 11, 27), saved.getTaskDate());
            assertEquals(TaskStatus.TODO, saved.getStatus());
            return saved;
        });
        when(studyPlanRepository.save(any(StudyPlan.class))).thenAnswer(inv -> inv.getArgument(0));
        when(classMemberRepository.findActiveClassRolesByUserId(10L)).thenReturn(List.of());
        when(eventRepository.findOverlappingOwnerEvents(eq(10L), any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(List.of());

        DecideStudyPlanItemRequest editReq = new DecideStudyPlanItemRequest();
        editReq.setDecision(StudyPlanItemDecision.EDITED);
        editReq.setTitle("  Ôn Slow Start nâng cao  ");
        editReq.setPlannedStart(Instant.parse("2026-11-27T13:00:00Z"));
        editReq.setPlannedEnd(Instant.parse("2026-11-27T14:30:00Z"));

        StudyPlanItemResponse res = studyPlanService.decidePlanItem(88L, 601L, editReq);
        assertEquals(StudyPlanItemDecision.EDITED, res.decision());
        assertEquals("Ôn Slow Start nâng cao", res.title());
        assertNotNull(res.taskId());
    }

    @Test
    @DisplayName("decidePlanItem throws CONFLICT when item already decided")
    void decidePlanItem_AlreadyDecided_ThrowsConflict() {
        StudyPlan plan = buildTwoItemPlan();
        plan.getItems().get(0).setDecision(StudyPlanItemDecision.ACCEPTED);

        when(studyPlanRepository.findWithItemsById(88L)).thenReturn(Optional.of(plan));

        DecideStudyPlanItemRequest req = new DecideStudyPlanItemRequest();
        req.setDecision(StudyPlanItemDecision.REJECTED);

        AppException ex = assertThrows(AppException.class, () -> studyPlanService.decidePlanItem(88L, 601L, req));
        assertEquals(ErrorCode.CONFLICT, ex.getErrorCode());
    }

    private StudyPlan buildTwoItemPlan() {
        AiJob job = AiJob.builder()
                .id("job-plan-1")
                .user(student)
                .inputJson(Map.of("goalId", 51L))
                .build();
        StudyPlan plan = StudyPlan.builder()
                .id(88L)
                .user(student)
                .job(job)
                .status(StudyPlanStatus.PROPOSED)
                .rationale("Ưu tiên ôn TCP.")
                .model("gemini-4-hydrogen")
                .promptVersion("plan-v2.0")
                .items(new ArrayList<>())
                .build();
        StudyPlanItem item1 = StudyPlanItem.builder()
                .id(601L)
                .plan(plan)
                .title("Ôn Slow Start")
                .plannedStart(Instant.parse("2026-11-26T12:30:00Z"))
                .plannedEnd(Instant.parse("2026-11-26T13:30:00Z"))
                .decision(StudyPlanItemDecision.PENDING)
                .build();
        StudyPlanItem item2 = StudyPlanItem.builder()
                .id(602L)
                .plan(plan)
                .title("Làm đề TCP")
                .plannedStart(Instant.parse("2026-11-26T15:00:00Z"))
                .plannedEnd(Instant.parse("2026-11-26T16:00:00Z"))
                .decision(StudyPlanItemDecision.PENDING)
                .build();
        plan.getItems().add(item1);
        plan.getItems().add(item2);
        return plan;
    }
}
