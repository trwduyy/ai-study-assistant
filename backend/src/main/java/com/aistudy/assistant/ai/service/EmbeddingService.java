package com.aistudy.assistant.ai.service;

/**
 * Generates and stores vector embeddings for extracted material chunks outside DB transactions.
 */
public interface EmbeddingService {

    void generateEmbeddingsForMaterial(Long materialId);
}
