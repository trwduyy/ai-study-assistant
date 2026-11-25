package com.aistudy.assistant.ai.dto;

import com.aistudy.assistant.ai.entity.AiJobStatus;

public record AiJobCancelResponse(
        String jobId,
        AiJobStatus status
) {
}
