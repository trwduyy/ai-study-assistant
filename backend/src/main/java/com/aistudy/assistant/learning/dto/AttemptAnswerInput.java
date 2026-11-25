package com.aistudy.assistant.learning.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AttemptAnswerInput(
        @NotNull(message = "questionId không được để trống.")
        Long questionId,
        List<Long> selectedOptionIds
) {
}
