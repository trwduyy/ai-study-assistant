package com.aistudy.assistant.chat.service;

import com.aistudy.assistant.chat.dto.ChatMessageResponse;
import com.aistudy.assistant.chat.dto.ConversationReadStateResponse;
import com.aistudy.assistant.chat.dto.ConversationResponse;
import com.aistudy.assistant.chat.dto.CreateDirectConversationRequest;
import com.aistudy.assistant.chat.dto.MarkConversationReadRequest;
import com.aistudy.assistant.chat.dto.SendMessageRequest;

import java.util.List;

public interface ChatService {

    List<ConversationResponse> getMyConversations();

    ConversationResponse openOrGetDirectConversation(CreateDirectConversationRequest request);

    CursorMessagePage getMessages(Long conversationId, Long cursor, int limit);

    ChatMessageResponse sendMessage(Long conversationId, Long senderId, SendMessageRequest request);

    ConversationReadStateResponse markAsRead(Long conversationId, MarkConversationReadRequest request);

    record CursorMessagePage(List<ChatMessageResponse> items, String nextCursor) {
    }
}
