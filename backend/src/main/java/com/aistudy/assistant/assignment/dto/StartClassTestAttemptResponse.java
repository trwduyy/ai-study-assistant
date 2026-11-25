package com.aistudy.assistant.assignment.dto;

import com.aistudy.assistant.learning.dto.QuizTakeQuestionDto;

import java.time.Instant;
import java.util.List;

/**
 * Response returned when starting a timed classroom test attempt, containing questions without answers.
 */
public record StartClassTestAttemptResponse(
        Long attemptId,
        Long testId,
        Long quizId,
        Instant startedAt,
        Instant deadlineAt,
        Integer version,
        List<QuizTakeQuestionDto> questions
) {
}
