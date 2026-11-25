package com.aistudy.assistant.ai.dto;

import java.time.Instant;

public record AiChatResponse(
        Long id,
        Long courseId,
        String title,
        Instant createdAt,
        Instant updatedAt
) {
}
