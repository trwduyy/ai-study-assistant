package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.QuizDifficulty;

import java.util.List;

public record QuizDetailResponse(
        Long id,
        Long courseId,
        String title,
        QuizDifficulty difficulty,
        Integer questionCount,
        Integer version,
        List<QuizTakeQuestionDto> questions
) {
}
