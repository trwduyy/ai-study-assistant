package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates and parses structured JSON flashcard payloads returned by Gemini.
 */
@Component
@RequiredArgsConstructor
public class AiFlashcardValidator {

    private final ObjectMapper objectMapper;

    public record GeneratedFlashcard(
            String front,
            String back,
            Long sourceChunkId
    ) {
    }

    public List<GeneratedFlashcard> parseAndValidate(String rawJson, Set<Long> allowedChunkIds) {
        if (!StringUtils.hasText(rawJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI trả về dữ liệu JSON flashcard rỗng.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Kết quả flashcard từ AI không phải JSON hợp lệ.");
        }

        JsonNode cardsNode = root.path("cards");
        if (!cardsNode.isArray() || cardsNode.isEmpty()) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI không tạo được thẻ flashcard nào hợp lệ.");
        }

        List<GeneratedFlashcard> cards = new ArrayList<>();
        for (int index = 0; index < cardsNode.size(); index++) {
            JsonNode cardNode = cardsNode.get(index);
            String front = cardNode.path("front").asText("").trim();
            String back = cardNode.path("back").asText("").trim();

            if (!StringUtils.hasText(front)) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Thẻ thứ " + (index + 1) + " bị thiếu nội dung mặt trước (front).");
            }
            if (!StringUtils.hasText(back)) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Thẻ thứ " + (index + 1) + " bị thiếu nội dung mặt sau (back).");
            }

            Long sourceChunkId = null;
            if (cardNode.has("sourceChunkId") && cardNode.get("sourceChunkId").canConvertToLong()) {
                long candidate = cardNode.get("sourceChunkId").asLong();
                if (allowedChunkIds != null && allowedChunkIds.contains(candidate)) {
                    sourceChunkId = candidate;
                }
            }

            cards.add(new GeneratedFlashcard(front, back, sourceChunkId));
        }
        return cards;
    }

    public Map<String, Object> buildFlashcardJsonSchema() {
        Map<String, Object> cardProps = new LinkedHashMap<>();
        cardProps.put("front", Map.of("type", "STRING"));
        cardProps.put("back", Map.of("type", "STRING"));
        cardProps.put("sourceChunkId", Map.of("type", "INTEGER"));

        Map<String, Object> cardSchema = new LinkedHashMap<>();
        cardSchema.put("type", "OBJECT");
        cardSchema.put("properties", cardProps);
        cardSchema.put("required", List.of("front", "back"));

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "cards", Map.of("type", "ARRAY", "items", cardSchema)
                ),
                "required", List.of("cards")
        );
    }
}
