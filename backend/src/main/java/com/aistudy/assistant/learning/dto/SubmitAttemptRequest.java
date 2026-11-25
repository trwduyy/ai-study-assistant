package com.aistudy.assistant.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitAttemptRequest(
        @NotNull(message = "Phiên bản (version) không được để trống.")
        Integer version,
        @Min(value = 0, message = "Thời gian làm bài không được âm.")
        Integer durationSeconds,
        @Valid
        List<AttemptAnswerInput> answers
) {
}
