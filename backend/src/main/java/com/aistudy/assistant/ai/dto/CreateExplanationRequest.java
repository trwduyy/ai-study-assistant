package com.aistudy.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateExplanationRequest(
        @NotNull(message = "materialId không được để trống.")
        Long materialId,
        @NotBlank(message = "Khái niệm cần giải thích không được để trống.")
        @Size(max = 300, message = "Khái niệm tối đa 300 ký tự.")
        String concept,
        @Size(max = 2000, message = "Ngữ cảnh trích đoạn tối đa 2000 ký tự.")
        String contextSnippet,
        Boolean allowExternalKnowledge
) {
}
