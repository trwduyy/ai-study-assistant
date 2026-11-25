package com.aistudy.assistant.learning.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ScoreTrendItemResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Long courseId,
        double score,
        double maxScore,
        Instant submittedAt
) {
    public ScoreTrendItemResponse(
            Long attemptId,
            Long quizId,
            String quizTitle,
            Long courseId,
            BigDecimal score,
            BigDecimal maxScore,
            Instant submittedAt) {
        this(
                attemptId,
                quizId,
                quizTitle,
                courseId,
                score != null ? score.doubleValue() : 0.0,
                maxScore != null ? maxScore.doubleValue() : 10.0,
                submittedAt
        );
    }
}
