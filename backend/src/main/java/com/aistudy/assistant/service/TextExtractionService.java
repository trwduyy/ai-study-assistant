package com.aistudy.assistant.service;

import java.nio.file.Path;

public interface TextExtractionService {

    String extractAndCleanText(Path filePath, String originalName);

    String cleanText(String rawText);
}
