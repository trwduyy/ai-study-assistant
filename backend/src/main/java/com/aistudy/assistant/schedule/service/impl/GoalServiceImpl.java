package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.schedule.dto.CreateGoalRequest;
import com.aistudy.assistant.schedule.dto.GoalResponse;
import com.aistudy.assistant.schedule.dto.UpdateGoalRequest;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.GoalStatus;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.aistudy.assistant.schedule.service.GoalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business service for managing personal study goals (FR-17).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoalServiceImpl implements GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<GoalResponse> listGoals(GoalStatus status, Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        return goalRepository.findByUserIdAndStatus(currentUserId, status, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public GoalResponse createGoal(CreateGoalRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        User user = userRepository.getReferenceById(currentUserId);

        Goal goal = Goal.builder()
                .user(user)
                .title(request.getTitle().trim())
                .subject(EventValidationSupport.trimToNull(request.getSubject()))
                .dueDate(request.getDueDate())
                .priority(request.getPriority() != null ? request.getPriority() : GoalPriority.MEDIUM)
                .status(GoalStatus.ACTIVE)
                .freeTime(request.getFreeTime())
                .build();
        Goal saved = goalRepository.save(goal);
        log.info("Created goal id={} by userId={}", saved.getId(), currentUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public GoalResponse updateGoal(Long goalId, UpdateGoalRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Goal goal = requireOwnedGoal(goalId, currentUserId);

        goal.setTitle(request.getTitle().trim());
        goal.setSubject(EventValidationSupport.trimToNull(request.getSubject()));
        goal.setDueDate(request.getDueDate());
        if (request.getPriority() != null) {
            goal.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            goal.setStatus(request.getStatus());
        }
        goal.setFreeTime(request.getFreeTime());

        Goal saved = goalRepository.save(goal);
        log.info("Updated goal id={} by userId={}", saved.getId(), currentUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteGoal(Long goalId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Goal goal = requireOwnedGoal(goalId, currentUserId);
        goalRepository.delete(goal);
        log.info("Deleted goal id={} by userId={}", goalId, currentUserId);
    }

    private Goal requireOwnedGoal(Long goalId, Long userId) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AppException(ErrorCode.GOAL_NOT_FOUND));
        if (!goal.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return goal;
    }

    private GoalResponse toResponse(Goal goal) {
        return new GoalResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getSubject(),
                goal.getDueDate(),
                goal.getPriority(),
                goal.getStatus(),
                goal.getFreeTime(),
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }
}
