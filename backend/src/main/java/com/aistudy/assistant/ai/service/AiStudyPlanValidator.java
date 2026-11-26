package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates and parses structured JSON study plan proposals returned by Gemini.
 */
@Component
@RequiredArgsConstructor
public class AiStudyPlanValidator {

    private final ObjectMapper objectMapper;

    public record GeneratedPlanItem(
            String title,
            Instant plannedStart,
            Instant plannedEnd
    ) {
    }

    public record GeneratedStudyPlan(
            String rationale,
            List<GeneratedPlanItem> items
    ) {
    }

    public GeneratedStudyPlan parseAndValidate(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI trả về dữ liệu lộ trình rỗng.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Kết quả lộ trình từ AI không phải JSON hợp lệ.");
        }

        String rationale = root.path("rationale").asText("").trim();
        if (!StringUtils.hasText(rationale)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Lộ trình từ AI thiếu phần giải thích lý do (rationale).");
        }

        JsonNode itemsNode = root.path("items");
        if (!itemsNode.isArray() || itemsNode.isEmpty()) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI không đề xuất được buổi học nào hợp lệ.");
        }

        List<GeneratedPlanItem> items = new ArrayList<>();
        for (int index = 0; index < itemsNode.size(); index++) {
            JsonNode itemNode = itemsNode.get(index);
            String title = itemNode.path("title").asText("").trim();
            if (!StringUtils.hasText(title)) {
                throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Buổi học thứ " + (index + 1) + " thiếu tiêu đề.");
            }
            if (title.length() > 255) {
                title = title.substring(0, 255);
            }

            Instant start = parseUtcTimestamp(itemNode.path("plannedStart").asText(null), index + 1, "plannedStart");
            Instant end = parseUtcTimestamp(itemNode.path("plannedEnd").asText(null), index + 1, "plannedEnd");
            if (!end.isAfter(start)) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Buổi học thứ " + (index + 1) + " có plannedEnd phải sau plannedStart."
                );
            }

            items.add(new GeneratedPlanItem(title, start, end));
        }
        return new GeneratedStudyPlan(rationale, items);
    }

    public Map<String, Object> buildStudyPlanJsonSchema() {
        Map<String, Object> itemProps = new LinkedHashMap<>();
        itemProps.put("title", Map.of("type", "STRING"));
        itemProps.put("plannedStart", Map.of("type", "STRING"));
        itemProps.put("plannedEnd", Map.of("type", "STRING"));

        Map<String, Object> itemSchema = new LinkedHashMap<>();
        itemSchema.put("type", "OBJECT");
        itemSchema.put("properties", itemProps);
        itemSchema.put("required", List.of("title", "plannedStart", "plannedEnd"));

        Map<String, Object> rootProps = new LinkedHashMap<>();
        rootProps.put("rationale", Map.of("type", "STRING"));
        rootProps.put("items", Map.of("type", "ARRAY", "items", itemSchema));

        return Map.of(
                "type", "OBJECT",
                "properties", rootProps,
                "required", List.of("rationale", "items")
        );
    }

    private Instant parseUtcTimestamp(String raw, int itemNumber, String fieldName) {
        if (!StringUtils.hasText(raw)) {
            throw new AppException(
                    ErrorCode.AI_OUTPUT_INVALID,
                    "Buổi học thứ " + itemNumber + " thiếu trường " + fieldName + "."
            );
        }
        String trimmed = raw.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeException ignored) {
            try {
                return OffsetDateTime.parse(trimmed).toInstant();
            } catch (DateTimeException ex) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Buổi học thứ " + itemNumber + " có " + fieldName + " không đúng chuẩn ISO-8601 UTC."
                );
            }
        }
    }
}
