package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.SummaryLength;
import jakarta.validation.constraints.NotNull;

public record CreateSummaryRequest(
        @NotNull(message = "materialId không được để trống.")
        Long materialId,
        SummaryLength length
) {
}
