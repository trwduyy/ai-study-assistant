package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.QuestionType;

import java.util.List;

public record ReviewQuestionDto(
        Long questionId,
        Integer position,
        String content,
        QuestionType type,
        List<ReviewOptionDto> options,
        List<Long> selectedOptionIds,
        boolean correct,
        String explanation,
        Long sourceChunkId
) {
}
