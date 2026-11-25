package com.aistudy.assistant.chat.dto;

import com.aistudy.assistant.chat.entity.MessageStatus;

import java.time.Instant;

/**
 * Chat message DTO returned by cursor-paginated history, REST send fallback, and STOMP broadcast.
 */
public record ChatMessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String senderName,
        String content,
        MessageStatus status,
        String clientMessageId,
        Instant createdAt
) {
}
