package com.aistudy.assistant.learning.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SaveAttemptDraftRequest(
        @NotNull(message = "Phiên bản (version) không được để trống.")
        Integer version,
        @NotNull(message = "Danh sách đáp án nháp không được để trống.")
        @Valid
        List<AttemptAnswerInput> answers
) {
}
