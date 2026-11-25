package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record QuizAttemptSummaryResponse(
        Long attemptId,
        AttemptStatus status,
        Double score,
        Double maxScore,
        Integer correctCount,
        Integer totalQuestions,
        Integer durationSeconds,
        Instant startedAt,
        Instant submittedAt
) {
    public QuizAttemptSummaryResponse(
            Long attemptId,
            AttemptStatus status,
            BigDecimal score,
            BigDecimal maxScore,
            Integer correctCount,
            Integer totalQuestions,
            Integer durationSeconds,
            Instant startedAt,
            Instant submittedAt) {
        this(
                attemptId,
                status,
                score != null ? score.doubleValue() : 0.0,
                maxScore != null ? maxScore.doubleValue() : 10.0,
                correctCount,
                totalQuestions,
                durationSeconds,
                startedAt,
                submittedAt
        );
    }
}
