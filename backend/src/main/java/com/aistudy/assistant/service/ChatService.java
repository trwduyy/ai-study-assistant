package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.chat.ChatAnswerResponse;
import com.aistudy.assistant.dto.chat.ChatMessageResponse;
import com.aistudy.assistant.dto.chat.ChatSessionResponse;
import com.aistudy.assistant.dto.chat.CreateChatSessionRequest;
import com.aistudy.assistant.dto.chat.SendMessageRequest;

import java.util.List;

public interface ChatService {

    ChatSessionResponse createSession(CreateChatSessionRequest request);

    List<ChatSessionResponse> getUserSessions(Long courseId);

    ChatSessionResponse getSession(Long sessionId);

    void deleteSession(Long sessionId);

    List<ChatMessageResponse> getSessionMessages(Long sessionId);

    ChatAnswerResponse sendMessage(Long sessionId, SendMessageRequest request);
}
