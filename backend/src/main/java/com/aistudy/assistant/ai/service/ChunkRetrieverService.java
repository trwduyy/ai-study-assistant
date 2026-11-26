package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;

import java.util.List;

/**
 * Abstraction for retrieving relevant document chunks (swappable for external vector stores such as Qdrant/pgvector).
 */
public interface ChunkRetrieverService {

    List<ChunkCandidate> retrieveRelevantChunks(String query, List<ChunkCandidate> candidates, int topK);

    double cosineSimilarity(float[] a, float[] b);
}
