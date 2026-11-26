package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.entity.MaterialChunkEmbedding;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.repository.MaterialChunkEmbeddingRepository;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Hybrid RAG retriever combining semantic cosine similarity over {@code material_chunk_embeddings}
 * with lexical keyword scoring and a configurable relevance threshold {@code minScore}.
 * Automatically falls back to lexical retrieval when embeddings are absent or Gemini is unavailable.
 */
@Slf4j
@Service
public class HybridChunkRetrieverServiceImpl implements ChunkRetrieverService {

    private static final double SEMANTIC_WEIGHT = 0.75;
    private static final double KEYWORD_WEIGHT = 0.25;

    private final MaterialChunkEmbeddingRepository embeddingRepository;
    private final GeminiClientService geminiClientService;
    private final ChunkGroundingService chunkGroundingService;
    private final ObjectMapper objectMapper;
    private final double minScore;

    @Autowired
    public HybridChunkRetrieverServiceImpl(
            MaterialChunkEmbeddingRepository embeddingRepository,
            GeminiClientService geminiClientService,
            ChunkGroundingService chunkGroundingService,
            ObjectMapper objectMapper,
            @Value("${app.rag.min-score:0.45}") double minScore) {
        this.embeddingRepository = embeddingRepository;
        this.geminiClientService = geminiClientService;
        this.chunkGroundingService = chunkGroundingService;
        this.objectMapper = objectMapper;
        this.minScore = Math.max(0.0, Math.min(1.0, minScore));
    }

    @Override
    public List<ChunkCandidate> retrieveRelevantChunks(String query, List<ChunkCandidate> candidates, int topK) {
        if (!StringUtils.hasText(query) || candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        if (!geminiClientService.isConfigured()) {
            return chunkGroundingService.selectTopChunks(query, candidates, topK);
        }

        List<Long> chunkIds = candidates.stream().map(ChunkCandidate::chunkId).toList();
        Map<Long, float[]> vectorsByChunkId = loadEmbeddings(chunkIds);
        if (vectorsByChunkId.isEmpty()) {
            return chunkGroundingService.selectTopChunks(query, candidates, topK);
        }

        float[] queryVector;
        try {
            queryVector = geminiClientService.embedText(query);
        } catch (Exception ex) {
            log.warn("Failed to embed query for hybrid search; falling back to keyword retrieval: {}", ex.getMessage());
            return chunkGroundingService.selectTopChunks(query, candidates, topK);
        }

        if (queryVector == null || queryVector.length == 0) {
            return chunkGroundingService.selectTopChunks(query, candidates, topK);
        }

        return candidates.stream()
                .map(candidate -> {
                    float[] chunkVector = vectorsByChunkId.get(candidate.chunkId());
                    double keywordScore = chunkGroundingService.normalizedKeywordScore(query, candidate.content());
                    double hybridScore;
                    if (chunkVector != null && chunkVector.length > 0) {
                        double semantic = Math.max(0.0, cosineSimilarity(queryVector, chunkVector));
                        hybridScore = (SEMANTIC_WEIGHT * semantic) + (KEYWORD_WEIGHT * keywordScore);
                    } else {
                        hybridScore = keywordScore;
                    }
                    return new ScoredHybridChunk(candidate, hybridScore);
                })
                .filter(scored -> scored.score() >= minScore)
                .sorted(Comparator.comparingDouble(ScoredHybridChunk::score).reversed()
                        .thenComparingInt(s -> s.candidate().chunkIndex()))
                .limit(topK)
                .map(ScoredHybridChunk::candidate)
                .toList();
    }

    @Override
    public double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private Map<Long, float[]> loadEmbeddings(List<Long> chunkIds) {
        List<MaterialChunkEmbedding> rows = embeddingRepository.findByChunkIdIn(chunkIds);
        if (rows == null || rows.isEmpty()) {
            return Map.of();
        }
        return rows.stream()
                .collect(Collectors.toMap(
                        row -> row.getChunk().getId(),
                        row -> deserializeVector(row.getEmbeddingJson()),
                        (first, ignored) -> first
                ));
    }

    private float[] deserializeVector(String json) {
        if (!StringUtils.hasText(json)) {
            return new float[0];
        }
        try {
            return objectMapper.readValue(json, float[].class);
        } catch (Exception ex) {
            log.warn("Failed to deserialize embedding vector: {}", ex.getMessage());
            return new float[0];
        }
    }

    private record ScoredHybridChunk(ChunkCandidate candidate, double score) {
    }
}
