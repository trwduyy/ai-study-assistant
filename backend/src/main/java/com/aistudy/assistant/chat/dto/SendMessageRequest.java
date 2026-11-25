package com.aistudy.assistant.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for sending a chat message via STOMP or REST fallback with client-generated deduplication ID.
 */
public record SendMessageRequest(
        @NotBlank(message = "clientMessageId không được để trống.")
        @Size(max = 64, message = "clientMessageId tối đa 64 ký tự.")
        String clientMessageId,

        @NotBlank(message = "Nội dung tin nhắn không được để trống.")
        @Size(max = 10000, message = "Nội dung tin nhắn tối đa 10000 ký tự.")
        String content
) {
}
