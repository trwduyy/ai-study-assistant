package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.FeedbackTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAiFeedbackRequest(
        @NotNull(message = "targetType không được để trống.")
        FeedbackTargetType targetType,
        @NotNull(message = "targetId không được để trống.")
        Long targetId,
        @NotBlank(message = "Lý do báo cáo không được để trống.")
        @Size(max = 2000, message = "Lý do tối đa 2000 ký tự.")
        String reason
) {
}
