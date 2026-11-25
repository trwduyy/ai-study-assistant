package com.aistudy.assistant.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record SendAiChatMessageRequest(
        @NotBlank(message = "Nội dung câu hỏi không được để trống.")
        @Size(max = 4000, message = "Câu hỏi tối đa 4000 ký tự.")
        String content,
        List<Long> materialIds,
        Boolean allowExternalKnowledge
) {
}
