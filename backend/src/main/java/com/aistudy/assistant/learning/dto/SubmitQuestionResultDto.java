package com.aistudy.assistant.learning.dto;

import java.util.List;

public record SubmitQuestionResultDto(
        Long questionId,
        List<Long> selectedOptionIds,
        List<Long> correctOptionIds,
        boolean correct,
        String explanation
) {
}
