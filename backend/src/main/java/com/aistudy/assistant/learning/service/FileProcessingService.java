package com.aistudy.assistant.learning.service;

import java.nio.file.Path;
import java.util.List;

public interface FileProcessingService {

    void extractAsync(Long materialId);

    ExtractionResult extractPagesFromFile(Path filePath, String fileName);

    String cleanText(String rawText);

    List<ChunkDraft> buildPageAwareChunks(List<PageText> pages);

    record PageText(Integer pageNumber, String text) {
    }

    record ExtractionResult(Integer pageCount, List<PageText> pages) {
    }

    record ChunkDraft(int chunkIndex, Integer pageFrom, Integer pageTo, String content, int tokenCount) {
    }
}
