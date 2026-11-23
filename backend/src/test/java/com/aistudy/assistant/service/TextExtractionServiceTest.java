package com.aistudy.assistant.service;

import com.aistudy.assistant.exception.UnprocessableEntityException;
import com.aistudy.assistant.service.impl.TextExtractionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextExtractionServiceTest {

    private TextExtractionServiceImpl textExtractionService;

    @BeforeEach
    void setUp() {
        textExtractionService = new TextExtractionServiceImpl();
    }

    @Test
    @DisplayName("Should successfully extract text from valid TXT file with more than 50 characters")
    void extractAndCleanText_ValidTxt_Success(@TempDir Path tempDir) throws IOException {
        String content = "Machine Learning is an important field of Artificial Intelligence. " +
                "It enables computers to learn from experience without being explicitly programmed.";

        Path txtFile = tempDir.resolve("sample_study_guide.txt");
        Files.writeString(txtFile, content);

        String result = textExtractionService.extractAndCleanText(txtFile, "sample_study_guide.txt");

        assertNotNull(result);
        assertTrue(result.length() >= 50);
        assertTrue(result.contains("Machine Learning"));
    }

    @Test
    @DisplayName("Should throw UnprocessableEntityException when extracted text is less than 50 characters")
    void extractAndCleanText_ShortText_ThrowsUnprocessableEntity(@TempDir Path tempDir) throws IOException {
        String shortContent = "Too short text.";

        Path txtFile = tempDir.resolve("short.txt");
        Files.writeString(txtFile, shortContent);

        UnprocessableEntityException ex = assertThrows(
                UnprocessableEntityException.class,
                () -> textExtractionService.extractAndCleanText(txtFile, "short.txt")
        );

        assertTrue(ex.getMessage().contains("less than 50 characters"));
    }

    @Test
    @DisplayName("Should throw UnprocessableEntityException when file is completely blank")
    void extractAndCleanText_EmptyFile_ThrowsUnprocessableEntity(@TempDir Path tempDir) throws IOException {
        Path emptyFile = tempDir.resolve("empty.txt");
        Files.writeString(emptyFile, "    \n\n   ");

        assertThrows(
                UnprocessableEntityException.class,
                () -> textExtractionService.extractAndCleanText(emptyFile, "empty.txt")
        );
    }

    @Test
    @DisplayName("Should clean whitespace, remove control chars, and preserve paragraphs")
    void cleanText_RemovesJunkAndNormalizesWhitespace() {
        String dirtyText = "Paragraph 1 line 1.   \t   Line 2.\u0000\u0007\r\n\r\n\r\n\r\nParagraph 2 here.";
        String cleaned = textExtractionService.cleanText(dirtyText);

        assertFalse(cleaned.contains("\u0000"));
        assertFalse(cleaned.contains("\u0007"));
        assertFalse(cleaned.contains("   \t   "));
        assertTrue(cleaned.contains("Paragraph 1 line 1. Line 2."));
        assertTrue(cleaned.contains("Paragraph 2 here."));
        assertEquals("Paragraph 1 line 1. Line 2.\n\nParagraph 2 here.", cleaned);
    }
}
