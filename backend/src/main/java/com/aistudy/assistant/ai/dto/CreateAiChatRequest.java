package com.aistudy.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAiChatRequest(
        @NotBlank(message = "Tiêu đề phiên chat không được để trống.")
        @Size(max = 200, message = "Tiêu đề tối đa 200 ký tự.")
        String title,
        Long courseId
) {
}
