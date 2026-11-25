package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.AiJobStatus;
import com.aistudy.assistant.ai.entity.AiJobType;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

public record AiJobResponse(
        String jobId,
        AiJobType type,
        AiJobStatus status,
        String resultType,
        Long resultId,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Object> resultPayload,
        String errorCode,
        String errorMessage,
        Integer attempts,
        Instant createdAt,
        Instant finishedAt
) {
}
