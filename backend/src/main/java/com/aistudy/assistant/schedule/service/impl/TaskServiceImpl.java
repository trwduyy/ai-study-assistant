package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.schedule.dto.CompleteTaskRequest;
import com.aistudy.assistant.schedule.dto.CreateTaskRequest;
import com.aistudy.assistant.schedule.dto.TaskResponse;
import com.aistudy.assistant.schedule.dto.UpdateTaskRequest;
import com.aistudy.assistant.schedule.entity.Goal;
import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.Task;
import com.aistudy.assistant.schedule.entity.TaskStatus;
import com.aistudy.assistant.schedule.repository.GoalRepository;
import com.aistudy.assistant.schedule.repository.TaskRepository;
import com.aistudy.assistant.schedule.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Business service for daily study tasks and completion toggles (FR-17).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<TaskResponse> listTasks(
            LocalDate from,
            LocalDate to,
            TaskStatus status,
            Long goalId,
            Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (from != null && to != null && to.isBefore(from)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Ngày kết thúc (to) không được trước ngày bắt đầu (from).",
                    List.of(new FieldErrorDetail("to", "Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu."))
            );
        }
        return taskRepository.findByUserIdAndFilters(currentUserId, from, to, status, goalId, pageable)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public TaskResponse createTask(CreateTaskRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Goal goal = resolveOwnedGoal(request.getGoalId(), currentUserId);
        User user = userRepository.getReferenceById(currentUserId);

        Task task = Task.builder()
                .user(user)
                .goal(goal)
                .title(request.getTitle().trim())
                .taskDate(request.getTaskDate())
                .priority(request.getPriority() != null ? request.getPriority() : GoalPriority.MEDIUM)
                .status(TaskStatus.TODO)
                .remindAt(request.getRemindAt())
                .build();
        Task saved = taskRepository.save(task);
        log.info("Created task id={} date={} by userId={}", saved.getId(), saved.getTaskDate(), currentUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public TaskResponse updateTask(Long taskId, UpdateTaskRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Task task = requireOwnedTask(taskId, currentUserId);
        Goal goal = resolveOwnedGoal(request.getGoalId(), currentUserId);

        task.setGoal(goal);
        task.setTitle(request.getTitle().trim());
        task.setTaskDate(request.getTaskDate());
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        task.setRemindAt(request.getRemindAt());

        Task saved = taskRepository.save(task);
        log.info("Updated task id={} by userId={}", saved.getId(), currentUserId);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public void deleteTask(Long taskId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Task task = requireOwnedTask(taskId, currentUserId);
        taskRepository.delete(task);
        log.info("Deleted task id={} by userId={}", taskId, currentUserId);
    }

    @Override
    @Transactional
    public TaskResponse toggleComplete(Long taskId, CompleteTaskRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Task task = requireOwnedTask(taskId, currentUserId);

        boolean markDone = request != null && request.getCompleted() != null
                ? request.getCompleted()
                : task.getStatus() != TaskStatus.DONE;

        task.setStatus(markDone ? TaskStatus.DONE : TaskStatus.TODO);
        Task saved = taskRepository.save(task);
        log.info("Updated completion status of task id={} to {} by userId={}",
                saved.getId(), saved.getStatus(), currentUserId);
        return toResponse(saved);
    }

    private Task requireOwnedTask(Long taskId, Long userId) {
        Task task = taskRepository.findWithDetailsById(taskId)
                .orElseThrow(() -> new AppException(ErrorCode.TASK_NOT_FOUND));
        if (!task.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return task;
    }

    private Goal resolveOwnedGoal(Long goalId, Long userId) {
        if (goalId == null) {
            return null;
        }
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new AppException(ErrorCode.GOAL_NOT_FOUND));
        if (!goal.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return goal;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getGoal() != null ? task.getGoal().getId() : null,
                task.getGoal() != null ? task.getGoal().getTitle() : null,
                task.getTitle(),
                task.getTaskDate(),
                task.getPriority(),
                task.getStatus(),
                task.getRemindAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
