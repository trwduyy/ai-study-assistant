package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.FeedbackStatus;
import com.aistudy.assistant.ai.entity.FeedbackTargetType;

import java.time.Instant;

public record AiFeedbackResponse(
        Long id,
        FeedbackTargetType targetType,
        Long targetId,
        String reason,
        FeedbackStatus status,
        Instant createdAt
) {
}
