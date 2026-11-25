package com.aistudy.assistant.ai.dto;

public record AiSourceCitationResponse(
        Long materialId,
        String fileName,
        Integer page,
        Long chunkId
) {
}
