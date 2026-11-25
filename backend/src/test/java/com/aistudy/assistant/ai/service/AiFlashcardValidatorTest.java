package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.service.AiFlashcardValidator.GeneratedFlashcard;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiFlashcardValidatorTest {

    private final AiFlashcardValidator validator = new AiFlashcardValidator(new ObjectMapper());

    @Test
    @DisplayName("parseAndValidate parses valid flashcard JSON and sanitizes unknown sourceChunkId")
    void parseAndValidate_ValidJson_Success() {
        String json = """
                {
                    "cards": [
                        {
                            "front": "TCP dùng cơ chế nào để thiết lập kết nối?",
                            "back": "Bắt tay ba bước SYN, SYN-ACK, ACK.",
                            "sourceChunkId": 884
                        },
                        {
                            "front": "UDP có đảm bảo thứ tự gói tin không?",
                            "back": "Không, UDP truyền datagram không kết nối.",
                            "sourceChunkId": 99999
                        }
                    ]
                }
                """;

        List<GeneratedFlashcard> cards = validator.parseAndValidate(json, Set.of(884L));

        assertEquals(2, cards.size());
        assertEquals("TCP dùng cơ chế nào để thiết lập kết nối?", cards.get(0).front());
        assertEquals(884L, cards.get(0).sourceChunkId());
        assertNull(cards.get(1).sourceChunkId());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when front is missing or blank")
    void parseAndValidate_MissingFront_ThrowsAiOutputInvalid() {
        String json = """
                {
                    "cards": [
                        {
                            "front": "   ",
                            "back": "Nội dung mặt sau"
                        }
                    ]
                }
                """;

        AppException ex = assertThrows(AppException.class, () -> validator.parseAndValidate(json, Set.of()));
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when back is missing")
    void parseAndValidate_MissingBack_ThrowsAiOutputInvalid() {
        String json = """
                {
                    "cards": [
                        {
                            "front": "Khái niệm TCP"
                        }
                    ]
                }
                """;

        AppException ex = assertThrows(AppException.class, () -> validator.parseAndValidate(json, Set.of()));
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }

    @Test
    @DisplayName("parseAndValidate throws AI_OUTPUT_INVALID when cards array is empty")
    void parseAndValidate_EmptyCards_ThrowsAiOutputInvalid() {
        AppException ex = assertThrows(
                AppException.class,
                () -> validator.parseAndValidate("{\"cards\": []}", Set.of())
        );
        assertEquals(ErrorCode.AI_OUTPUT_INVALID, ex.getErrorCode());
    }
}
