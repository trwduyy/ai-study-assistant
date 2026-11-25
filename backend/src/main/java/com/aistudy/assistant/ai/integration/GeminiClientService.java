package com.aistudy.assistant.ai.integration;

import java.util.Map;

/**
 * HTTP client abstraction for invoking the configured Gemini AI provider.
 */
public interface GeminiClientService {

    String generateText(String systemPrompt, String userPrompt);

    String generateStructuredJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema);

    boolean isConfigured();

    String getModel();
}
