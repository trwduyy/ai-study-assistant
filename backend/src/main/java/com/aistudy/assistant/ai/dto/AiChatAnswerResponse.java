package com.aistudy.assistant.ai.dto;

import java.time.Instant;
import java.util.List;

public record AiChatAnswerResponse(
        Long messageId,
        String answer,
        Boolean grounded,
        List<AiSourceCitationResponse> sources,
        String aiAddedNote,
        String model,
        String promptVersion,
        Instant createdAt
) {
}
