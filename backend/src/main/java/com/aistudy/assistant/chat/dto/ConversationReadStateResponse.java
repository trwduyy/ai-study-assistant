package com.aistudy.assistant.chat.dto;

import java.time.Instant;

/**
 * Read-state watermark response for a conversation.
 */
public record ConversationReadStateResponse(
        Long conversationId,
        Long lastReadMessageId,
        Instant updatedAt
) {
}
