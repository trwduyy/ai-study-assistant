package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.time.Instant;

/**
 * Server-graded result returned after submitting a classroom test attempt.
 */
public record ClassTestSubmitResultResponse(
        Long attemptId,
        Long testId,
        Long quizId,
        AttemptStatus status,
        Double score,
        Double maxScore,
        Integer correctCount,
        Integer totalQuestions,
        Integer durationSeconds,
        Instant submittedAt
) {
}
