package com.aistudy.assistant.ai.integration;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RestClient-based Gemini REST API integration with configurable timeout, retries, generation, and embeddings.
 * Callers must invoke this service strictly outside database transactions.
 */
@Slf4j
@Service
public class GeminiClientServiceImpl implements GeminiClientService {

    private static final String GEMINI_API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models";
    private static final int MAX_RETRIES = 2;

    private final String apiKey;
    private final String model;
    private final String embeddingModel;
    private final long retryBackoffMs;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GeminiClientServiceImpl(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-4-hydrogen}") String model,
            @Value("${app.gemini.embedding-model:text-embedding-004}") String embeddingModel,
            @Value("${app.gemini.timeout-seconds:60}") int timeoutSeconds,
            @Value("${app.gemini.retry-backoff-ms:1500}") long retryBackoffMs,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = StringUtils.hasText(model) ? model.trim() : "gemini-4-hydrogen";
        this.embeddingModel = StringUtils.hasText(embeddingModel) ? embeddingModel.trim() : "text-embedding-004";
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    GeminiClientServiceImpl(
            String apiKey,
            String model,
            long retryBackoffMs,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this(apiKey, model, "text-embedding-004", retryBackoffMs, restClient, objectMapper);
    }

    GeminiClientServiceImpl(
            String apiKey,
            String model,
            String embeddingModel,
            long retryBackoffMs,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey != null ? apiKey.trim() : "";
        this.model = StringUtils.hasText(model) ? model.trim() : "gemini-4-hydrogen";
        this.embeddingModel = StringUtils.hasText(embeddingModel) ? embeddingModel.trim() : "text-embedding-004";
        this.retryBackoffMs = Math.max(0L, retryBackoffMs);
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @Override
    public String getModel() {
        return model;
    }

    @Override
    public String getEmbeddingModel() {
        return embeddingModel;
    }

    @Override
    public String generateText(String systemPrompt, String userPrompt) {
        Map<String, Object> requestPayload = buildRequestPayload(systemPrompt, userPrompt, false, null);
        String url = String.format("%s/%s:generateContent?key=%s", GEMINI_API_BASE_URL, model, apiKey);
        String responseBody = executePostWithRetry(url, model, requestPayload);
        return extractTextFromResponse(responseBody);
    }

    @Override
    public String generateStructuredJson(String systemPrompt, String userPrompt, Map<String, Object> jsonSchema) {
        Map<String, Object> requestPayload = buildRequestPayload(systemPrompt, userPrompt, true, jsonSchema);
        String url = String.format("%s/%s:generateContent?key=%s", GEMINI_API_BASE_URL, model, apiKey);
        String responseBody = executePostWithRetry(url, model, requestPayload);
        return cleanJsonOutput(extractTextFromResponse(responseBody));
    }

    @Override
    public float[] embedText(String text) {
        if (!StringUtils.hasText(text)) {
            return new float[0];
        }
        Map<String, Object> payload = Map.of(
                "model", "models/" + embeddingModel,
                "content", Map.of("parts", List.of(Map.of("text", text)))
        );
        String url = String.format("%s/%s:embedContent?key=%s", GEMINI_API_BASE_URL, embeddingModel, apiKey);
        String responseBody = executePostWithRetry(url, embeddingModel, payload);
        return extractSingleEmbedding(responseBody);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        if (texts.size() == 1) {
            return List.of(embedText(texts.get(0)));
        }

        List<Map<String, Object>> requests = texts.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/" + embeddingModel,
                        "content", Map.of("parts", List.of(Map.of("text", text != null ? text : "")))
                ))
                .toList();
        Map<String, Object> payload = Map.of("requests", requests);

        String url = String.format("%s/%s:batchEmbedContents?key=%s", GEMINI_API_BASE_URL, embeddingModel, apiKey);
        String responseBody = executePostWithRetry(url, embeddingModel, payload);
        return extractBatchEmbeddings(responseBody, texts.size());
    }

    private Map<String, Object> buildRequestPayload(
            String systemPrompt,
            String userPrompt,
            boolean isJsonOutput,
            Map<String, Object> jsonSchema) {
        Map<String, Object> payload = new HashMap<>();

        if (StringUtils.hasText(systemPrompt)) {
            payload.put("system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }

        payload.put("contents", List.of(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userPrompt != null ? userPrompt : ""))
        )));

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

    private String executePostWithRetry(String url, String targetModel, Map<String, Object> requestPayload) {
        if (!isConfigured()) {
            throw new AppException(
                    ErrorCode.AI_PROVIDER_ERROR,
                    "Dịch vụ AI chưa được cấu hình GEMINI_API_KEY."
            );
        }

        int attempts = 0;
        while (attempts <= MAX_RETRIES) {
            attempts++;
            try {
                log.info("Calling Gemini model '{}' (attempt {}/{})", targetModel, attempts, MAX_RETRIES + 1);
                return restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestPayload)
                        .retrieve()
                        .body(String.class);

            } catch (RestClientResponseException ex) {
                int statusCode = ex.getStatusCode().value();
                String errorSummary = extractErrorMessage(ex.getResponseBodyAsString());
                log.warn("Gemini API returned HTTP {} on attempt {}: {}", statusCode, attempts, errorSummary);

                boolean retryable = statusCode >= 500 || statusCode == 429;
                if (retryable && attempts <= MAX_RETRIES) {
                    sleepBackoff(attempts);
                    continue;
                }

                if (statusCode == 504) {
                    throw new AppException(ErrorCode.AI_TIMEOUT);
                }
                if (statusCode == 429) {
                    throw new AppException(
                            ErrorCode.AI_PROVIDER_ERROR,
                            "Hệ thống AI đang quá tải. Vui lòng thử lại sau."
                    );
                }
                throw new AppException(
                        ErrorCode.AI_PROVIDER_ERROR,
                        "Lỗi từ dịch vụ AI (" + statusCode + "): " + errorSummary
                );

            } catch (ResourceAccessException ex) {
                log.warn("Gemini API network/timeout error on attempt {}: {}", attempts, ex.getMessage());
                if (attempts <= MAX_RETRIES) {
                    sleepBackoff(attempts);
                    continue;
                }
                if (isTimeoutCause(ex)) {
                    throw new AppException(ErrorCode.AI_TIMEOUT);
                }
                throw new AppException(
                        ErrorCode.AI_PROVIDER_ERROR,
                        "Không thể kết nối tới dịch vụ AI sau nhiều lần thử."
                );

            } catch (AppException ex) {
                throw ex;
            } catch (Exception ex) {
                log.error("Unexpected error while invoking Gemini API: {}", ex.getMessage(), ex);
                if (attempts <= MAX_RETRIES) {
                    sleepBackoff(attempts);
                    continue;
                }
                throw new AppException(ErrorCode.AI_PROVIDER_ERROR);
            }
        }

        throw new AppException(ErrorCode.AI_PROVIDER_ERROR);
    }

    private float[] extractSingleEmbedding(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Dịch vụ embedding trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode values = root.path("embedding").path("values");
            float[] vector = toFloatArray(values);
            if (vector.length == 0) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Vector embedding từ AI bị rỗng.");
            }
            return vector;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không thể đọc vector embedding từ dịch vụ AI.");
        }
    }

    private List<float[]> extractBatchEmbeddings(String responseJson, int expectedCount) {
        if (!StringUtils.hasText(responseJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Dịch vụ batch embedding trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode embeddingsNode = root.path("embeddings");
            if (!embeddingsNode.isArray() || embeddingsNode.size() != expectedCount) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Số lượng vector embedding trả về không khớp.");
            }
            List<float[]> vectors = new ArrayList<>(expectedCount);
            for (JsonNode item : embeddingsNode) {
                float[] vector = toFloatArray(item.path("values"));
                if (vector.length == 0) {
                    throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Vector embedding trong batch bị rỗng.");
                }
                vectors.add(vector);
            }
            return vectors;
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không thể đọc batch vector embedding từ dịch vụ AI.");
        }
    }

    private float[] toFloatArray(JsonNode valuesNode) {
        if (!valuesNode.isArray() || valuesNode.isEmpty()) {
            return new float[0];
        }
        float[] result = new float[valuesNode.size()];
        for (int i = 0; i < valuesNode.size(); i++) {
            result[i] = (float) valuesNode.get(i).asDouble();
        }
        return result;
    }

    private void sleepBackoff(int attempt) {
        if (retryBackoffMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(retryBackoffMs * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AppException(ErrorCode.AI_PROVIDER_ERROR, "Yêu cầu tới dịch vụ AI bị gián đoạn.");
        }
    }

    private boolean isTimeoutCause(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof SocketTimeoutException || cursor instanceof HttpTimeoutException) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private String extractTextFromResponse(String responseJson) {
        if (!StringUtils.hasText(responseJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Dịch vụ AI trả về phản hồi rỗng.");
        }
        try {
            JsonNode root = objectMapper.readTree(responseJson);
            JsonNode candidates = root.path("candidates");

            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                if (parts.isArray() && !parts.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (JsonNode part : parts) {
                        if (part.has("text")) {
                            sb.append(part.get("text").asText());
                        }
                    }
                    if (!sb.isEmpty()) {
                        return sb.toString();
                    }
                }
            }

            JsonNode promptFeedback = root.path("promptFeedback");
            if (promptFeedback.has("blockReason")) {
                throw new AppException(
                        ErrorCode.AI_PROVIDER_ERROR,
                        "Nội dung bị bộ lọc an toàn của AI từ chối: " + promptFeedback.get("blockReason").asText()
                );
            }

            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không nhận được nội dung hợp lệ từ mô hình AI.");
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to parse Gemini API envelope: {}", ex.getMessage());
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Không thể đọc phản hồi từ dịch vụ AI.");
        }
    }

    private String cleanJsonOutput(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
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
            return "Lỗi không xác định";
        }
        try {
            JsonNode node = objectMapper.readTree(errorBody);
            if (node.has("error") && node.get("error").has("message")) {
                return node.get("error").get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return errorBody.length() > 300 ? errorBody.substring(0, 300) : errorBody;
    }
}
