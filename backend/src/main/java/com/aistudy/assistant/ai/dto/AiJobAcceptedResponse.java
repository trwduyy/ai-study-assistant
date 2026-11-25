package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.AiJobStatus;

public record AiJobAcceptedResponse(
        String jobId,
        AiJobStatus status
) {
}
