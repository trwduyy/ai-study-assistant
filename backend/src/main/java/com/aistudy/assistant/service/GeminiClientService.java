package com.aistudy.assistant.service;

import java.util.Map;

public interface GeminiClientService {

    String generateText(String systemPrompt, String userPrompt);

    String generateStructuredJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema);

    boolean isConfigured();
}
