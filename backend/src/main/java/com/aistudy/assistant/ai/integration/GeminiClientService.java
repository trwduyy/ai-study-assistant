package com.aistudy.assistant.ai.integration;

import java.util.List;
import java.util.Map;

/**
 * HTTP client abstraction for invoking the configured Gemini AI provider (generation and embeddings).
 */
public interface GeminiClientService {

    String generateText(String systemPrompt, String userPrompt);

    String generateStructuredJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema);

    float[] embedText(String text);

    List<float[]> embedBatch(List<String> texts);

    boolean isConfigured();

    String getModel();

    String getEmbeddingModel();
}
