package com.aistudy.assistant.chat.dto;

import com.aistudy.assistant.chat.entity.ConversationType;

import java.time.Instant;

/**
 * Conversation list/detail DTO including title, unread count, and last message preview.
 */
public record ConversationResponse(
        Long id,
        ConversationType type,
        Long classId,
        String title,
        long unreadCount,
        LastMessageSummary lastMessage,
        Instant updatedAt
) {
    public record LastMessageSummary(
            Long id,
            Long senderId,
            String senderName,
            String content,
            Instant createdAt
    ) {
    }
}
