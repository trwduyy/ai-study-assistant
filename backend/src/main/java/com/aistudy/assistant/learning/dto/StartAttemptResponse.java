package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.AttemptStatus;

import java.time.Instant;
import java.util.List;

public record StartAttemptResponse(
        Long attemptId,
        Long quizId,
        AttemptStatus status,
        Instant startedAt,
        Instant deadlineAt,
        Double maxScore,
        Integer version,
        List<AttemptAnswerInput> answers
) {
}
