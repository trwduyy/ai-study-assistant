package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.time.Instant;
import java.util.List;

public record QuizSubmitResultResponse(
        Long attemptId,
        Long quizId,
        AttemptStatus status,
        Double score,
        Double maxScore,
        Integer correctCount,
        Integer totalQuestions,
        Integer durationSeconds,
        Instant submittedAt,
        List<SubmitQuestionResultDto> details
) {
}
