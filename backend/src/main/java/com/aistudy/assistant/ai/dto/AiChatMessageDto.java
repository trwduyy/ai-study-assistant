package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

public record AiChatMessageDto(
        Long id,
        AiMessageRole role,
        String content,
        Boolean grounded,
        List<AiSourceCitationResponse> sources,
        String aiAddedNote,
        @JsonInclude(JsonInclude.Include.NON_NULL) String model,
        @JsonInclude(JsonInclude.Include.NON_NULL) String promptVersion,
        Instant createdAt
) {
}
