package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.SummaryLength;

import java.time.Instant;

public record AiSummaryResponse(
        Long id,
        Long materialId,
        SummaryLength length,
        String content,
        String model,
        String promptVersion,
        Instant createdAt,
        Instant updatedAt
) {
}
