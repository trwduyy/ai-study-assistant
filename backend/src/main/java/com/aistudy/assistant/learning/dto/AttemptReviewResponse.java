package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.time.Instant;
import java.util.List;

public record AttemptReviewResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        AttemptStatus status,
        Double score,
        Double maxScore,
        Integer correctCount,
        Integer totalQuestions,
        Integer durationSeconds,
        Instant submittedAt,
        List<ReviewQuestionDto> questions
) {
}
