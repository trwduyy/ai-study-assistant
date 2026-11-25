package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.QuizDifficulty;

import java.time.Instant;

public record QuizSummaryResponse(
        Long id,
        Long courseId,
        String title,
        QuizDifficulty difficulty,
        Integer questionCount,
        String model,
        String promptVersion,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
