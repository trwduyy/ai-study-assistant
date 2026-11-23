package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.exception.ServiceUnavailableException;
import com.aistudy.assistant.service.GeminiClientService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiClientServiceImpl implements GeminiClientService {

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_BACKOFF_MS = 1500L;

    private final String apiKey;
    private final String model;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public GeminiClientServiceImpl(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-1.5-flash}") String model,
            @Value("${app.gemini.timeout-seconds:60}") int timeoutSeconds,
            ObjectMapper objectMapper) {

        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = StringUtils.hasText(model) ? model.trim() : "gemini-1.5-flash";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        Map<String, Object> requestPayload = buildRequestPayload(systemPrompt, userPrompt, false, null);
        return executeGeminiRequestWithRetry(requestPayload);
    }

    @Override
    public String generateStructuredJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        Map<String, Object> requestPayload = buildRequestPayload(systemPrompt, userPrompt, true, jsonSchema);
        String responseText = executeGeminiRequestWithRetry(requestPayload);

        // Sanitize markdown fences if present (e.g. ```json ... ```)
        return cleanJsonOutput(responseText);
    }

    private Map<String, Object> buildRequestPayload(
            String systemPrompt,
            String userPrompt,
            boolean isJsonOutput,
            Map<String, Object> jsonSchema) {

        Map<String, Object> payload = new HashMap<>();

        if (StringUtils.hasText(systemPrompt)) {
            Map<String, Object> systemPart = Map.of("text", systemPrompt);
            payload.put("system_instruction", Map.of("parts", List.of(systemPart)));
        }

        Map<String, Object> userPart = Map.of("text", userPrompt);
        Map<String, Object> userContent = Map.of("role", "user", "parts", List.of(userPart));
        payload.put("contents", List.of(userContent));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", isJsonOutput ? 0.2 : 0.4);
        generationConfig.put("maxOutputTokens", 8192);

        if (isJsonOutput) {
            generationConfig.put("responseMimeType", "application/json");
            if (jsonSchema != null && !jsonSchema.isEmpty()) {
                generationConfig.put("responseSchema", jsonSchema);
            }
        }

        payload.put("generationConfig", generationConfig);
        return payload;
    }

    private String executeGeminiRequestWithRetry(Map<String, Object> requestPayload) {
        if (!isConfigured()) {
            log.error("Gemini API key is missing. Please set GEMINI_API_KEY in your environment configuration.");
            throw new ServiceUnavailableException(
                    "Gemini AI service is not configured. Please configure a valid GEMINI_API_KEY."
            );
        }

        String url = String.format("%s/%s:generateContent?key=%s", GEMINI_API_BASE_URL, model, apiKey);

        int attempts = 0;
        while (attempts <= MAX_RETRIES) {
            try {
                attempts++;
                log.info("Sending request to Gemini model '{}' (attempt {}/{})", model, attempts, MAX_RETRIES + 1);

                String responseBody = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestPayload)
                        .retrieve()
                        .body(String.class);

                return extractTextFromResponse(responseBody);

            } catch (RestClientResponseException ex) {
                HttpStatusCode status = ex.getStatusCode();
                int statusCode = status.value();
                String errorBody = ex.getResponseBodyAsString();

                log.warn("Gemini API returned HTTP status {}: {}", statusCode, errorBody);

                boolean isTransientError = statusCode == 429 || statusCode == 503 || statusCode == 500;
                if (isTransientError && attempts <= MAX_RETRIES) {
                    log.info("Transient error from Gemini ({}). Retrying in {} ms...", statusCode, RETRY_BACKOFF_MS);
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ServiceUnavailableException("Gemini request was interrupted");
                    }
                    continue;
                }

                if (statusCode == 400) {
                    throw new ServiceUnavailableException("Gemini API Bad Request: " + extractErrorMessage(errorBody));
                } else if (statusCode == 401 || statusCode == 403) {
                    throw new ServiceUnavailableException("Gemini API authentication failed. Please verify your GEMINI_API_KEY.");
                } else if (statusCode == 429) {
                    throw new ServiceUnavailableException("Gemini API quota exceeded or rate limited. Please try again shortly.");
                } else {
                    throw new ServiceUnavailableException("Gemini AI service failed with status " + statusCode + ": " + extractErrorMessage(errorBody));
                }

            } catch (Exception ex) {
                log.error("Network or unexpected error while connecting to Gemini API: {}", ex.getMessage(), ex);
                if (attempts <= MAX_RETRIES) {
                    try {
                        Thread.sleep(RETRY_BACKOFF_MS * attempts);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ServiceUnavailableException("Gemini request was interrupted");
                    }
                    continue;
                }
                throw new ServiceUnavailableException("Unable to connect to Gemini AI service: " + ex.getMessage());
            }
        }

        throw new ServiceUnavailableException("Gemini AI service is temporarily unavailable after retries");
    }

    private String extractTextFromResponse(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            throw new ServiceUnavailableException("Empty response received from Gemini API");
        }

        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode firstCandidate = candidates.get(0);
                JsonNode parts = firstCandidate.path("content").path("parts");

                if (parts.isArray() && !parts.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            sb.append(part.get("text").asText());
                        }
                    }
                    return sb.toString();
                }
            }

            // Check if blocked by safety ratings
            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.has("blockReason")) {
                String blockReason = promptFeedback.get("blockReason").asText();
                throw new ServiceUnavailableException("Content was blocked by safety filters: " + blockReason);
            }

            throw new ServiceUnavailableException("No valid text candidate returned from Gemini model");

        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Gemini response JSON: {}", ex.getMessage(), ex);
            throw new ServiceUnavailableException("Failed to parse Gemini AI response: " + ex.getMessage());
        }
    }

    private String cleanJsonOutput(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "[]";
        }

        String cleaned = rawText.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    private String extractErrorMessage(String errorBody) {
        if (!StringUtils.hasText(errorBody)) {
            return "Unknown error";
        }
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            if (node.has("error") && node.get("error").has("message")) {
                return node.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return errorBody;
    }
}
