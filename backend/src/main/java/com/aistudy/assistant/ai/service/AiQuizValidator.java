package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Validates structured Gemini quiz JSON responses to ensure sufficient options, valid answer counts, and explanations.
 */
@Component
@RequiredArgsConstructor
public class AiQuizValidator {

    private static final int MIN_OPTIONS_PER_QUESTION = 2;

    private final ObjectMapper objectMapper;

    public record GeneratedOption(
            String label,
            String content,
            boolean isCorrect
    ) {
    }

    public record GeneratedQuestion(
            String content,
            String explanation,
            Long sourceChunkId,
            List<GeneratedOption> options
    ) {
    }

    public Map<String, Object> buildQuizJsonSchema() {
        Map<String, Object> optionProperties = new LinkedHashMap<>();
        optionProperties.put("label", Map.of("type", "STRING"));
        optionProperties.put("content", Map.of("type", "STRING"));
        optionProperties.put("isCorrect", Map.of("type", "BOOLEAN"));

        Map<String, Object> optionSchema = Map.of(
                "type", "OBJECT",
                "properties", optionProperties,
                "required", List.of("label", "content", "isCorrect")
        );

        Map<String, Object> questionProperties = new LinkedHashMap<>();
        questionProperties.put("content", Map.of("type", "STRING"));
        questionProperties.put("explanation", Map.of("type", "STRING"));
        questionProperties.put("sourceChunkId", Map.of("type", "INTEGER"));
        questionProperties.put("options", Map.of("type", "ARRAY", "items", optionSchema));

        Map<String, Object> questionSchema = Map.of(
                "type", "OBJECT",
                "properties", questionProperties,
                "required", List.of("content", "explanation", "options")
        );

        return Map.of(
                "type", "OBJECT",
                "properties", Map.of(
                        "questions", Map.of("type", "ARRAY", "items", questionSchema)
                ),
                "required", List.of("questions")
        );
    }

    public List<GeneratedQuestion> parseAndValidate(
            String rawJson,
            QuestionType questionType,
            Set<Long> allowedChunkIds) {
        if (!StringUtils.hasText(rawJson)) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI trả về dữ liệu quiz rỗng.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(rawJson);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "Dữ liệu JSON quiz từ AI không hợp lệ.");
        }

        JsonNode questionsNode = root.isArray() ? root : root.path("questions");
        if (!questionsNode.isArray() || questionsNode.isEmpty()) {
            throw new AppException(ErrorCode.AI_OUTPUT_INVALID, "AI không tạo được câu hỏi nào hợp lệ.");
        }

        QuestionType effectiveType = questionType != null ? questionType : QuestionType.SINGLE_CHOICE;
        List<GeneratedQuestion> parsedQuestions = new ArrayList<>();

        for (int i = 0; i < questionsNode.size(); i++) {
            JsonNode qNode = questionsNode.get(i);
            String content = textOrNull(qNode, "content");
            String explanation = textOrNull(qNode, "explanation");

            if (!StringUtils.hasText(content)) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Câu hỏi thứ " + (i + 1) + " bị thiếu nội dung."
                );
            }
            if (!StringUtils.hasText(explanation)) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Câu hỏi thứ " + (i + 1) + " bị thiếu phần giải thích đáp án."
                );
            }

            JsonNode optionsNode = qNode.path("options");
            if (!optionsNode.isArray() || optionsNode.size() < MIN_OPTIONS_PER_QUESTION) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Câu hỏi thứ " + (i + 1) + " không đủ số lượng đáp án."
                );
            }

            List<GeneratedOption> parsedOptions = new ArrayList<>();
            Set<String> seenLabels = new HashSet<>();
            int correctCount = 0;

            for (int optIdx = 0; optIdx < optionsNode.size(); optIdx++) {
                JsonNode optNode = optionsNode.get(optIdx);
                String label = textOrNull(optNode, "label");
                String optionContent = textOrNull(optNode, "content");
                JsonNode correctNode = optNode.get("isCorrect");

                if (!StringUtils.hasText(label)) {
                    label = String.valueOf((char) ('A' + optIdx));
                } else {
                    label = label.trim().toUpperCase(Locale.ROOT);
                }

                if (label.length() > 10 || !seenLabels.add(label)) {
                    throw new AppException(
                            ErrorCode.AI_OUTPUT_INVALID,
                            "Câu hỏi thứ " + (i + 1) + " có nhãn đáp án bị trùng hoặc không hợp lệ."
                    );
                }
                if (!StringUtils.hasText(optionContent)) {
                    throw new AppException(
                            ErrorCode.AI_OUTPUT_INVALID,
                            "Câu hỏi thứ " + (i + 1) + " có đáp án rỗng."
                    );
                }

                boolean isCorrect = correctNode != null && correctNode.isBoolean() && correctNode.asBoolean();
                if (isCorrect) {
                    correctCount++;
                }
                parsedOptions.add(new GeneratedOption(label, optionContent.trim(), isCorrect));
            }

            if (effectiveType == QuestionType.SINGLE_CHOICE && correctCount != 1) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Câu hỏi trắc nghiệm một đáp án thứ " + (i + 1)
                                + " phải có đúng 1 đáp án đúng (hiện có " + correctCount + ")."
                );
            }
            if (effectiveType == QuestionType.MULTI_CHOICE && correctCount < 1) {
                throw new AppException(
                        ErrorCode.AI_OUTPUT_INVALID,
                        "Câu hỏi trắc nghiệm nhiều đáp án thứ " + (i + 1) + " phải có ít nhất 1 đáp án đúng."
                );
            }

            Long sourceChunkId = null;
            JsonNode chunkNode = qNode.get("sourceChunkId");
            if (chunkNode != null && chunkNode.canConvertToLong()) {
                long candidateId = chunkNode.asLong();
                if (allowedChunkIds == null || allowedChunkIds.isEmpty() || allowedChunkIds.contains(candidateId)) {
                    sourceChunkId = candidateId;
                }
            }

            parsedQuestions.add(new GeneratedQuestion(
                    content.trim(),
                    explanation.trim(),
                    sourceChunkId,
                    parsedOptions
            ));
        }

        return parsedQuestions;
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.get(fieldName);
        return field != null && field.isTextual() ? field.asText() : null;
    }
}
