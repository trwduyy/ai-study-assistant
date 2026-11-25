package com.aistudy.assistant.ai.dto;

import java.util.List;

public record AiChatDetailResponse(
        Long id,
        Long courseId,
        String title,
        List<AiChatMessageDto> messages
) {
}
