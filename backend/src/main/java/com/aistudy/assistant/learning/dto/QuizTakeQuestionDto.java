package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.QuestionType;

import java.util.List;

public record QuizTakeQuestionDto(
        Long id,
        Long topicId,
        Integer position,
        String content,
        QuestionType type,
        List<QuizTakeOptionDto> options
) {
}
