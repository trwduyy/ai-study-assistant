package com.aistudy.assistant.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for opening or retrieving a 1-1 direct conversation.
 */
public record CreateDirectConversationRequest(
        @NotNull(message = "Mã người dùng không được để trống.")
        Long userId
) {
}
