package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.entity.MaterialChunkEmbedding;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.repository.MaterialChunkEmbeddingRepository;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates chunk embeddings via Gemini outside DB transactions.
 * Embedding failures are logged and never revert a material's {@code EXTRACTED} status.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final int EMBEDDING_BATCH_SIZE = 50;

    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final MaterialChunkEmbeddingRepository embeddingRepository;
    private final GeminiClientService geminiClientService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public void generateEmbeddingsForMaterial(Long materialId) {
        if (materialId == null || !geminiClientService.isConfigured()) {
            log.debug("Skipping chunk embedding generation for materialId={} (Gemini unconfigured or null)", materialId);
            return;
        }

        List<ChunkTextSnapshot> chunks = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return materialChunkRepository.findByMaterialIdOrderByChunkIndexAsc(materialId).stream()
                    .map(c -> new ChunkTextSnapshot(c.getId(), c.getContent()))
                    .toList();
        });

        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<float[]> allVectors = new ArrayList<>(chunks.size());
        try {
            for (int start = 0; start < chunks.size(); start += EMBEDDING_BATCH_SIZE) {
                int end = Math.min(start + EMBEDDING_BATCH_SIZE, chunks.size());
                List<String> batchTexts = chunks.subList(start, end).stream()
                        .map(ChunkTextSnapshot::content)
                        .toList();
                List<float[]> batchVectors = geminiClientService.embedBatch(batchTexts);
                if (batchVectors.size() != batchTexts.size()) {
                    log.warn("Embedding batch size mismatch for materialId={}: expected={} actual={}",
                            materialId, batchTexts.size(), batchVectors.size());
                    return;
                }
                allVectors.addAll(batchVectors);
            }
        } catch (Exception ex) {
            log.warn("Embedding generation failed for materialId={} (keeping EXTRACTED status): {}",
                    materialId, ex.getMessage());
            return;
        }

        String modelName = geminiClientService.getEmbeddingModel();
        transactionTemplate.executeWithoutResult(status -> {
            embeddingRepository.deleteByMaterialId(materialId);
            embeddingRepository.flush();

            Material materialRef = materialRepository.getReferenceById(materialId);
            List<MaterialChunkEmbedding> entities = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                float[] vector = allVectors.get(i);
                entities.add(MaterialChunkEmbedding.builder()
                        .chunk(materialChunkRepository.getReferenceById(chunks.get(i).chunkId()))
                        .material(materialRef)
                        .model(modelName)
                        .dimensions(vector.length)
                        .embeddingJson(serializeVector(vector))
                        .build());
            }
            embeddingRepository.saveAll(entities);
        });

        log.info("Generated {} embeddings (model={}) for materialId={}", allVectors.size(), modelName, materialId);
    }

    private String serializeVector(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize embedding vector", ex);
        }
    }

    private record ChunkTextSnapshot(Long chunkId, String content) {
    }
}
