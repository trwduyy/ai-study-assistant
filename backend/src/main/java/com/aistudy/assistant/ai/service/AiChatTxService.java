package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiChatAnswerResponse;
import com.aistudy.assistant.ai.dto.AiSourceCitationResponse;
import com.aistudy.assistant.ai.entity.AiChat;
import com.aistudy.assistant.ai.entity.AiChatMessage;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.aistudy.assistant.ai.entity.AiMessageSource;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiChatRepository;
import com.aistudy.assistant.ai.repository.AiMessageSourceRepository;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Short-lived transactional persistence helper for grounded AI chat turns.
 * Ensures DB connections are never kept open while calling Gemini.
 */
@Service
@RequiredArgsConstructor
public class AiChatTxService {

    private final AiChatRepository aiChatRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final AiMessageSourceRepository aiMessageSourceRepository;
    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;

    public record ChatHistoryTurn(AiMessageRole role, String content) {
    }

    public record PreparedChatTurn(
            Long chatId,
            List<ChunkCandidate> candidateChunks,
            List<ChatHistoryTurn> recentHistory
    ) {
    }

    @Transactional
    public PreparedChatTurn prepareAndSaveUserTurn(
            Long chatId,
            Long userId,
            String questionContent,
            List<Long> requestedMaterialIds) {
        AiChat chat = aiChatRepository.findWithDetailsById(chatId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_CHAT_NOT_FOUND));
        if (!chat.getUser().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }

        List<Long> targetMaterialIds = resolveTargetMaterialIds(chat, userId, requestedMaterialIds);
        List<ChunkCandidate> candidateChunks = targetMaterialIds.isEmpty()
                ? List.of()
                : materialChunkRepository.findWithMaterialByMaterialIdIn(targetMaterialIds).stream()
                        .map(chunk -> new ChunkCandidate(
                                chunk.getId(),
                                chunk.getMaterial().getId(),
                                chunk.getMaterial().getFileName(),
                                chunk.getPageFrom(),
                                chunk.getPageTo(),
                                chunk.getContent(),
                                chunk.getChunkIndex()
                        ))
                        .toList();

        List<AiChatMessage> recent = aiChatMessageRepository.findTop6ByChatIdOrderByIdDesc(chatId);
        List<ChatHistoryTurn> history = new ArrayList<>(recent.stream()
                .map(msg -> new ChatHistoryTurn(msg.getRole(), msg.getContent()))
                .toList());
        Collections.reverse(history);

        AiChatMessage userMessage = AiChatMessage.builder()
                .chat(chat)
                .role(AiMessageRole.USER)
                .content(questionContent)
                .grounded(Boolean.TRUE)
                .build();
        aiChatMessageRepository.save(userMessage);

        return new PreparedChatTurn(chatId, candidateChunks, history);
    }

    @Transactional
    public AiChatAnswerResponse saveAssistantTurn(
            Long chatId,
            String answer,
            boolean grounded,
            List<ChunkCandidate> citedChunks,
            String aiAddedNote,
            String model,
            String promptVersion) {
        AiChat chat = aiChatRepository.findById(chatId)
                .orElseThrow(() -> new AppException(ErrorCode.AI_CHAT_NOT_FOUND));

        AiChatMessage assistantMessage = AiChatMessage.builder()
                .chat(chat)
                .role(AiMessageRole.ASSISTANT)
                .content(answer)
                .grounded(grounded)
                .aiAddedNote(aiAddedNote)
                .model(model)
                .promptVersion(promptVersion)
                .build();
        assistantMessage = aiChatMessageRepository.save(assistantMessage);

        chat.setUpdatedAt(Instant.now());
        aiChatRepository.save(chat);

        List<AiSourceCitationResponse> sourceResponses = new ArrayList<>();
        if (grounded && citedChunks != null && !citedChunks.isEmpty()) {
            List<AiMessageSource> entities = new ArrayList<>();
            for (ChunkCandidate chunk : citedChunks) {
                Material materialRef = materialRepository.getReferenceById(chunk.materialId());
                MaterialChunk chunkRef = materialChunkRepository.getReferenceById(chunk.chunkId());
                entities.add(AiMessageSource.builder()
                        .message(assistantMessage)
                        .material(materialRef)
                        .chunk(chunkRef)
                        .page(chunk.pageFrom())
                        .build());
                sourceResponses.add(new AiSourceCitationResponse(
                        chunk.materialId(),
                        chunk.fileName(),
                        chunk.pageFrom(),
                        chunk.chunkId()
                ));
            }
            aiMessageSourceRepository.saveAll(entities);
        }

        return new AiChatAnswerResponse(
                assistantMessage.getId(),
                assistantMessage.getContent(),
                assistantMessage.getGrounded(),
                sourceResponses,
                assistantMessage.getAiAddedNote(),
                assistantMessage.getModel(),
                assistantMessage.getPromptVersion(),
                assistantMessage.getCreatedAt()
        );
    }

    private List<Long> resolveTargetMaterialIds(
            AiChat chat,
            Long userId,
            List<Long> requestedMaterialIds) {
        if (requestedMaterialIds != null && !requestedMaterialIds.isEmpty()) {
            List<Long> distinctIds = requestedMaterialIds.stream().distinct().toList();
            List<Material> materials = materialRepository.findByIdInAndDeletedAtIsNull(distinctIds);
            if (materials.size() != distinctIds.size()) {
                throw new AppException(ErrorCode.MATERIAL_NOT_FOUND);
            }
            Map<Long, Material> byId = materials.stream().collect(Collectors.toMap(Material::getId, Function.identity()));
            for (Long materialId : distinctIds) {
                Material material = byId.get(materialId);
                if (!material.getOwner().getId().equals(userId)) {
                    throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
                }
                if (material.getStatus() != MaterialStatus.EXTRACTED) {
                    throw new AppException(ErrorCode.TEXT_NOT_EXTRACTED);
                }
            }
            return distinctIds;
        }

        if (chat.getCourse() != null) {
            return materialRepository.findByCourseIdAndOwnerIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                    chat.getCourse().getId(),
                    userId,
                    MaterialStatus.EXTRACTED
            ).stream().map(Material::getId).toList();
        }

        return materialRepository.findByOwnerIdAndStatusAndDeletedAtIsNullOrderByUpdatedAtDesc(
                userId,
                MaterialStatus.EXTRACTED
        ).stream().map(Material::getId).toList();
    }
}
