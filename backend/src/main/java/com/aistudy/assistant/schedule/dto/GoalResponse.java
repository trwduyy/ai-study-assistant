package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.GoalPriority;
import com.aistudy.assistant.schedule.entity.GoalStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

public record GoalResponse(
        Long id,
        String title,
        String subject,
        LocalDate dueDate,
        GoalPriority priority,
        GoalStatus status,
        Map<String, Object> freeTime,
        Instant createdAt,
        Instant updatedAt
) {
}
