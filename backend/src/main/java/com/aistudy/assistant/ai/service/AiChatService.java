package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiChatAnswerResponse;
import com.aistudy.assistant.ai.dto.AiChatDetailResponse;
import com.aistudy.assistant.ai.dto.AiChatResponse;
import com.aistudy.assistant.ai.dto.CreateAiChatRequest;
import com.aistudy.assistant.ai.dto.SendAiChatMessageRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AiChatService {

    record ChatDetailSlice(
            AiChatDetailResponse detail,
            int size,
            Long nextCursor
    ) {
    }

    AiChatResponse createChat(CreateAiChatRequest request);

    Page<AiChatResponse> listChats(Long courseId, Pageable pageable);

    ChatDetailSlice getChatDetail(Long chatId, Long cursor, int limit);

    void deleteChat(Long chatId);

    AiChatAnswerResponse sendMessage(Long chatId, SendAiChatMessageRequest request);
}
