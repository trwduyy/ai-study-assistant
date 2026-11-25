package com.aistudy.assistant.chat.mapper;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.ConversationResponse.LastMessageSummary;
import com.aistudy.assistant.chat.entity.Message;
import org.springframework.stereotype.Component;

/**
 * Maps {@code chat} module entities to REST and WebSocket DTOs.
 */
@Component
public class ChatMapper {

    public ChatMessageResponse toChatMessageResponse(Message message) {
        return new ChatMessageResponse(
                message.getId(),
                message.getConversation().getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getStatus(),
                message.getClientMessageId(),
                message.getCreatedAt()
        );
    }

    public LastMessageSummary toLastMessageSummary(Message message) {
        if (message == null) {
            return null;
        }
        return new LastMessageSummary(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getFullName(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
