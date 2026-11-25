package com.aistudy.assistant.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Request payload for advancing the caller's read watermark in a conversation.
 */
public record MarkConversationReadRequest(
        @NotNull(message = "lastReadMessageId không được để trống.")
        Long lastReadMessageId
) {
}
