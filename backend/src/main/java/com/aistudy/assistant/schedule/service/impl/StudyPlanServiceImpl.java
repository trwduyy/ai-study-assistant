package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.schedule.dto.ConflictingEventSummary;
import com.aistudy.assistant.schedule.dto.DecideStudyPlanItemRequest;
import com.aistudy.assistant.schedule.dto.StudyPlanItemResponse;
import com.aistudy.assistant.schedule.dto.StudyPlanResponse;
import com.aistudy.assistant.schedule.entity.Event;
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
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.aistudy.assistant.schedule.service.StudyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Business service for viewing AI-proposed study plans and reviewing/accepting individual items (BR-11).
 * Never overwrites existing calendar events automatically; accepted/edited items create explicit {@link Task} rows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanServiceImpl implements StudyPlanService {

    private final StudyPlanRepository studyPlanRepository;
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final EventRepository eventRepository;
    private final ClassMemberRepository classMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public StudyPlanResponse getStudyPlan(Long planId) {
        Long currentUserId = SecurityUtils.currentUserId();
        StudyPlan plan = requireOwnedPlan(planId, currentUserId);

        List<Event> existingEvents = loadEventsCoveringItems(currentUserId, plan.getItems());
        List<StudyPlanItemResponse> itemDtos = plan.getItems().stream()
                .map(item -> toItemResponse(plan, item, findConflicts(item.getPlannedStart(), item.getPlannedEnd(), existingEvents)))
                .toList();

        return new StudyPlanResponse(
                plan.getId(),
                plan.getJob() != null ? plan.getJob().getId() : null,
                plan.getStatus(),
                plan.getRationale(),
                plan.getModel(),
                plan.getPromptVersion(),
                itemDtos,
                plan.getCreatedAt(),
                plan.getUpdatedAt()
        );
    }

    @Override
    @Transactional
    public StudyPlanItemResponse decidePlanItem(Long planId, Long itemId, DecideStudyPlanItemRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (request.getDecision() == StudyPlanItemDecision.PENDING) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Quyết định hợp lệ là ACCEPTED, EDITED hoặc REJECTED.",
                    List.of(new FieldErrorDetail("decision", "Không thể đặt lại trạng thái PENDING."))
            );
        }

        StudyPlan plan = requireOwnedPlan(planId, currentUserId);
        StudyPlanItem item = plan.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_PLAN_ITEM_NOT_FOUND));

        if (item.getDecision() != StudyPlanItemDecision.PENDING) {
            throw new AppException(ErrorCode.CONFLICT, "Mục lộ trình này đã được xử lý trước đó.");
        }

        if (request.getDecision() == StudyPlanItemDecision.REJECTED) {
            item.setDecision(StudyPlanItemDecision.REJECTED);
        } else {
            String effectiveTitle = StringUtils.hasText(request.getTitle())
                    ? request.getTitle().trim()
                    : item.getTitle();
            Instant effectiveStart = request.getPlannedStart() != null
                    ? request.getPlannedStart()
                    : item.getPlannedStart();
            Instant effectiveEnd = request.getPlannedEnd() != null
                    ? request.getPlannedEnd()
                    : item.getPlannedEnd();

            if (!StringUtils.hasText(effectiveTitle)) {
                throw new AppException(ErrorCode.VALIDATION_ERROR, "Tiêu đề buổi học không được để trống.");
            }
            EventValidationSupport.validateTimeWindow(effectiveStart, effectiveEnd);

            Goal linkedGoal = resolveGoalFromPlanJob(plan, currentUserId);
            Task task = Task.builder()
                    .user(plan.getUser())
                    .goal(linkedGoal)
                    .title(effectiveTitle)
                    .taskDate(effectiveStart.atZone(ZoneOffset.UTC).toLocalDate())
                    .priority(linkedGoal != null ? linkedGoal.getPriority() : GoalPriority.MEDIUM)
                    .status(TaskStatus.TODO)
                    .remindAt(effectiveStart)
                    .build();
            Task savedTask = taskRepository.save(task);

            item.setTitle(effectiveTitle);
            item.setPlannedStart(effectiveStart);
            item.setPlannedEnd(effectiveEnd);
            item.setDecision(request.getDecision());
            item.setTask(savedTask);
        }

        boolean hasRemainingPending = plan.getItems().stream()
                .anyMatch(candidate -> candidate.getDecision() == StudyPlanItemDecision.PENDING);
        plan.setStatus(hasRemainingPending ? StudyPlanStatus.PARTIAL : StudyPlanStatus.CLOSED);

        StudyPlan savedPlan = studyPlanRepository.save(plan);
        List<ConflictingEventSummary> conflicts = item.getDecision() == StudyPlanItemDecision.REJECTED
                ? List.of()
                : findConflicts(
                        item.getPlannedStart(),
                        item.getPlannedEnd(),
                        loadEventsCoveringItems(currentUserId, List.of(item))
                );

        log.info("Processed studyPlanItem id={} planId={} decision={} planStatus={} taskId={}",
                itemId, planId, item.getDecision(), savedPlan.getStatus(),
                item.getTask() != null ? item.getTask().getId() : null);
        return toItemResponse(savedPlan, item, conflicts);
    }

    private StudyPlan requireOwnedPlan(Long planId, Long userId) {
        StudyPlan plan = studyPlanRepository.findWithItemsById(planId)
                .orElseThrow(() -> new AppException(ErrorCode.STUDY_PLAN_NOT_FOUND));
        if (!plan.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return plan;
    }

    private Goal resolveGoalFromPlanJob(StudyPlan plan, Long userId) {
        if (plan.getJob() == null || plan.getJob().getInputJson() == null) {
            return null;
        }
        Map<String, Object> input = plan.getJob().getInputJson();
        Object rawGoalId = input.get("goalId");
        Long goalId = null;
        if (rawGoalId instanceof Number n) {
            goalId = n.longValue();
        } else if (rawGoalId instanceof String s && StringUtils.hasText(s)) {
            try {
                goalId = Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        if (goalId == null) {
            return null;
        }
        return goalRepository.findByIdAndUserId(goalId, userId).orElse(null);
    }

    private List<Event> loadEventsCoveringItems(Long userId, List<StudyPlanItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Instant rangeFrom = items.stream().map(StudyPlanItem::getPlannedStart).min(Instant::compareTo).orElseThrow();
        Instant rangeTo = items.stream().map(StudyPlanItem::getPlannedEnd).max(Instant::compareTo).orElseThrow();

        List<Long> activeClassIds = classMemberRepository.findActiveClassRolesByUserId(userId).stream()
                .map(ActiveClassRoleProjection::getClassId)
                .distinct()
                .toList();
        return activeClassIds.isEmpty()
                ? eventRepository.findOverlappingOwnerEvents(userId, rangeFrom, rangeTo, null)
                : eventRepository.findOverlappingCalendarEvents(userId, activeClassIds, rangeFrom, rangeTo, null);
    }

    private List<ConflictingEventSummary> findConflicts(
            Instant start,
            Instant end,
            List<Event> candidateEvents) {
        if (candidateEvents == null || candidateEvents.isEmpty()) {
            return List.of();
        }
        return candidateEvents.stream()
                .filter(existing -> existing.getStartAt().isBefore(end) && start.isBefore(existing.getEndAt()))
                .map(existing -> new ConflictingEventSummary(
                        existing.getId(),
                        existing.getTitle(),
                        existing.getType(),
                        existing.getClassroom() != null ? existing.getClassroom().getId() : null,
                        existing.getClassroom() != null ? existing.getClassroom().getName() : null,
                        existing.getStartAt(),
                        existing.getEndAt(),
                        existing.getSource()
                ))
                .toList();
    }

    private StudyPlanItemResponse toItemResponse(
            StudyPlan plan,
            StudyPlanItem item,
            List<ConflictingEventSummary> conflicts) {
        return new StudyPlanItemResponse(
                item.getId(),
                plan.getId(),
                plan.getStatus(),
                item.getTitle(),
                item.getPlannedStart(),
                item.getPlannedEnd(),
                item.getDecision(),
                item.getTask() != null ? item.getTask().getId() : null,
                conflicts != null && !conflicts.isEmpty(),
                conflicts != null ? conflicts : List.of()
        );
    }
}
