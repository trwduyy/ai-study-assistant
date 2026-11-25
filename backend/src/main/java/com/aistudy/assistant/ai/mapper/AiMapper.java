package com.aistudy.assistant.ai.mapper;

import com.aistudy.assistant.ai.dto.AiChatMessageDto;
import com.aistudy.assistant.ai.dto.AiChatResponse;
import com.aistudy.assistant.ai.dto.AiFeedbackResponse;
import com.aistudy.assistant.ai.dto.AiJobResponse;
import com.aistudy.assistant.ai.dto.AiSourceCitationResponse;
import com.aistudy.assistant.ai.dto.AiSummaryResponse;
import com.aistudy.assistant.ai.entity.AiChat;
import com.aistudy.assistant.ai.entity.AiChatMessage;
import com.aistudy.assistant.ai.entity.AiFeedback;
import com.aistudy.assistant.ai.entity.AiJob;
import com.aistudy.assistant.ai.entity.AiMessageSource;
import com.aistudy.assistant.ai.entity.AiSummary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Pure DTO mapper for the AI module ensuring JPA entities are never exposed over REST APIs.
 */
@Component
public class AiMapper {

    @SuppressWarnings("unchecked")
    public AiJobResponse toJobResponse(AiJob job) {
        Map<String, Object> resultPayload = null;
        if (job.getInputJson() != null && job.getInputJson().get("output") instanceof Map<?, ?> outputMap) {
            resultPayload = (Map<String, Object>) outputMap;
        }
        return new AiJobResponse(
                job.getId(),
                job.getType(),
                job.getStatus(),
                job.getResultType(),
                job.getResultId(),
                resultPayload,
                job.getErrorCode(),
                job.getErrorMessage(),
                job.getAttempts(),
                job.getCreatedAt(),
                job.getFinishedAt()
        );
    }

    public AiSummaryResponse toSummaryResponse(AiSummary summary) {
        return new AiSummaryResponse(
                summary.getId(),
                summary.getMaterial().getId(),
                summary.getLength(),
                summary.getContent(),
                summary.getModel(),
                summary.getPromptVersion(),
                summary.getCreatedAt(),
                summary.getUpdatedAt()
        );
    }

    public AiChatResponse toChatResponse(AiChat chat) {
        return new AiChatResponse(
                chat.getId(),
                chat.getCourse() != null ? chat.getCourse().getId() : null,
                chat.getTitle(),
                chat.getCreatedAt(),
                chat.getUpdatedAt()
        );
    }

    public AiSourceCitationResponse toSourceCitation(AiMessageSource source) {
        return new AiSourceCitationResponse(
                source.getMaterial().getId(),
                source.getMaterial().getFileName(),
                source.getPage(),
                source.getChunk() != null ? source.getChunk().getId() : null
        );
    }

    public AiChatMessageDto toMessageDto(AiChatMessage message, List<AiMessageSource> sources) {
        List<AiSourceCitationResponse> citations = sources == null
                ? List.of()
                : sources.stream().map(this::toSourceCitation).toList();
        return new AiChatMessageDto(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getGrounded(),
                citations,
                message.getAiAddedNote(),
                message.getModel(),
                message.getPromptVersion(),
                message.getCreatedAt()
        );
    }

    public AiFeedbackResponse toFeedbackResponse(AiFeedback feedback) {
        return new AiFeedbackResponse(
                feedback.getId(),
                feedback.getTargetType(),
                feedback.getTargetId(),
                feedback.getReason(),
                feedback.getStatus(),
                feedback.getCreatedAt()
        );
    }
}
