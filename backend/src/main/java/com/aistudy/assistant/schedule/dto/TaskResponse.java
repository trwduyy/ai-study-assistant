package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record TaskResponse(
        Long id,
        Long goalId,
        String goalTitle,
        String title,
        LocalDate taskDate,
        GoalPriority priority,
        TaskStatus status,
        Instant remindAt,
        Instant createdAt,
        Instant updatedAt
) {
}
