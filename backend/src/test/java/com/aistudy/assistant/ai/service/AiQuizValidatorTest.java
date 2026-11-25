package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.service.AiQuizValidator.GeneratedQuestion;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.QuestionType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiQuizValidatorTest {

    private AiQuizValidator validator;

    @BeforeEach
    void setUp() {
        validator = new AiQuizValidator(new ObjectMapper());
    }

    @Test
    @DisplayName("parseAndValidate accepts valid single-choice quiz JSON and filters unknown chunk IDs")
    void parseAndValidate_AcceptsValidPayload() {
        String validJson = """
                {
                    "questions": [
                        {
                            "content": "TCP thuộc tầng nào trong mô hình OSI?",
                            "explanation": "Theo slide trang 12, TCP hoạt động tại tầng giao vận (Transport).",
                            "sourceChunkId": 884,
                            "options": [
                                { "label": "A", "content": "Tầng giao vận", "isCorrect": true },
                                { "label": "B", "content": "Tầng mạng", "isCorrect": false },
                                { "label": "C", "content": "Tầng liên kết dữ liệu", "isCorrect": false },
                                { "label": "D", "content": "Tầng vật lý", "isCorrect": false }
                            ]
                        },
                        {
                            "content": "UDP là giao thức gì?",
                            "explanation": "UDP truyền không kết nối.",
                            "sourceChunkId": 9999,
                            "options": [
                                { "label": "A", "content": "Không kết nối", "isCorrect": true },
                                { "label": "B", "content": "Hướng kết nối", "isCorrect": false }
                            ]
                        }
                    ]
                }
                """;

        List<GeneratedQuestion> parsed = validator.parseAndValidate(
                validJson,
                QuestionType.SINGLE_CHOICE,
                Set.of(884L)
        );

        assertEquals(2, parsed.size());
        assertEquals(884L, parsed.get(0).sourceChunkId());
        assertNull(parsed.get(1).sourceChunkId());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when SINGLE_CHOICE question has zero correct options")
    void parseAndValidate_RejectsMissingCorrectOption() {
        String missingCorrectJson = """
                {
                    "questions": [
                        {
                            "content": "Giao thức nào đảm bảo độ tin cậy?",
                            "explanation": "TCP đảm bảo độ tin cậy.",
                            "options": [
                                { "label": "A", "content": "TCP", "isCorrect": false },
                                { "label": "B", "content": "UDP", "isCorrect": false },
                                { "label": "C", "content": "IP", "isCorrect": false },
                                { "label": "D", "content": "ARP", "isCorrect": false }
                            ]
                        }
                    ]
                }
                """;

        AppException ex = assertThrows(
                AppException.class,
                () -> validator.parseAndValidate(missingCorrectJson, QuestionType.SINGLE_CHOICE, Set.of())
        );
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when SINGLE_CHOICE question has multiple correct options")
    void parseAndValidate_RejectsMultipleCorrectForSingleChoice() {
        String twoCorrectJson = """
                {
                    "questions": [
                        {
                            "content": "Câu hỏi mẫu?",
                            "explanation": "Giải thích rõ ràng.",
                            "options": [
                                { "label": "A", "content": "Phương án A", "isCorrect": true },
                                { "label": "B", "content": "Phương án B", "isCorrect": true }
                            ]
                        }
                    ]
                }
                """;

        AppException ex = assertThrows(
                AppException.class,
                () -> validator.parseAndValidate(twoCorrectJson, QuestionType.SINGLE_CHOICE, Set.of())
        );
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when explanation is missing")
    void parseAndValidate_RejectsMissingExplanation() {
        String missingExplanationJson = """
                {
                    "questions": [
                        {
                            "content": "Câu hỏi mẫu?",
                            "explanation": "",
                            "options": [
                                { "label": "A", "content": "Đáp án A", "isCorrect": true },
                                { "label": "B", "content": "Đáp án B", "isCorrect": false }
                            ]
                        }
                    ]
                }
                """;

        AppException ex = assertThrows(
                AppException.class,
                () -> validator.parseAndValidate(missingExplanationJson, QuestionType.SINGLE_CHOICE, Set.of())
        );
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }
}
