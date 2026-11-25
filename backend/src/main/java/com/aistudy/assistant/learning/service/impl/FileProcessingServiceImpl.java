package com.aistudy.assistant.learning.service.impl;

import com.aistudy.assistant.ai.service.EmbeddingService;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.service.FileProcessingService;
import com.aistudy.assistant.learning.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Asynchronous document parser and page-aware chunking engine using PDFBox and Apache POI.
 * File I/O executes strictly outside database transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileProcessingServiceImpl implements FileProcessingService {

    public static final int MIN_EXTRACTABLE_CHARS = 50;
    public static final int TARGET_CHUNK_SIZE = 1000;
    public static final int MAX_CHUNK_SIZE = 1200;
    public static final int MIN_CHUNK_SIZE = 800;
    public static final int CHUNK_OVERLAP = 100;

    private final MaterialRepository materialRepository;
    private final MaterialChunkRepository materialChunkRepository;
    private final FileStorageService fileStorageService;
    private final EmbeddingService embeddingService;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Async("fileExtractionExecutor")
    public void extractAsync(Long materialId) {
        MaterialMetadata metadata = transactionTemplate.execute(status -> loadMaterialForExtraction(materialId));
        if (metadata == null) {
            return;
        }

        try {
            Path filePath = fileStorageService.resolvePath(metadata.storedPath());
            ExtractionResult result = extractPagesFromFile(filePath, metadata.fileName());

            int totalChars = result.pages().stream()
                    .mapToInt(page -> page.text() != null ? page.text().length() : 0)
                    .sum();

            if (totalChars < MIN_EXTRACTABLE_CHARS) {
                log.warn("Material id={} yielded only {} extractable characters; marking FAILED", materialId, totalChars);
                transactionTemplate.executeWithoutResult(status -> markFailed(
                        materialId,
                        result.pageCount(),
                        ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.name(),
                        ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.getDefaultMessage()
                ));
                return;
            }

            List<ChunkDraft> chunkDrafts = buildPageAwareChunks(result.pages());
            if (chunkDrafts.isEmpty()) {
                transactionTemplate.executeWithoutResult(status -> markFailed(
                        materialId,
                        result.pageCount(),
                        ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.name(),
                        ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.getDefaultMessage()
                ));
                return;
            }

            transactionTemplate.executeWithoutResult(status -> persistExtractedChunks(materialId, result.pageCount(), chunkDrafts));
            log.info("Material id={} extracted successfully: pages={}, chunks={}", materialId, result.pageCount(), chunkDrafts.size());

            try {
                embeddingService.generateEmbeddingsForMaterial(materialId);
            } catch (Exception ex) {
                log.warn("Post-extraction embedding generation failed for materialId={}: {}", materialId, ex.getMessage());
            }
        } catch (Exception ex) {
            log.error("Async extraction failed for material id={}", materialId, ex);
            transactionTemplate.executeWithoutResult(status -> markFailed(
                    materialId,
                    null,
                    ErrorCode.INVALID_FORMAT.name(),
                    "Không thể trích xuất văn bản từ tệp tài liệu. Vui lòng kiểm tra định dạng tệp."
            ));
        }
    }

    @Override
    public ExtractionResult extractPagesFromFile(Path filePath, String fileName) {
        String extension = extractExtension(fileName);
        try {
            return switch (extension) {
                case "pdf" -> extractPdfPages(filePath);
                case "docx" -> extractDocx(filePath);
                case "txt" -> extractTxt(filePath);
                default -> throw new IllegalArgumentException("Unsupported extension: " + extension);
            };
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file for extraction: " + fileName, ex);
        }
    }

    @Override
    public String cleanText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        text = text.replaceAll("[ \\t]+", " ");
        text = text.replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");
        text = text.replaceAll("\\n{3,}", "\n\n");
        return text.trim();
    }

    @Override
    public List<ChunkDraft> buildPageAwareChunks(List<PageText> pages) {
        List<TextSegment> segments = toSegments(pages);
        if (segments.isEmpty()) {
            return List.of();
        }

        List<ChunkDraft> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        Integer currentPageFrom = null;
        Integer currentPageTo = null;
        int chunkIndex = 0;

        for (TextSegment segment : segments) {
            if (buffer.isEmpty()) {
                currentPageFrom = segment.pageNumber();
                currentPageTo = segment.pageNumber();
                buffer.append(segment.text());
                continue;
            }

            int projectedLength = buffer.length() + 2 + segment.text().length();
            if (projectedLength <= TARGET_CHUNK_SIZE || (buffer.length() < MIN_CHUNK_SIZE && projectedLength <= MAX_CHUNK_SIZE)) {
                buffer.append("\n\n").append(segment.text());
                currentPageTo = maxPage(currentPageTo, segment.pageNumber());
            } else {
                String finalizedContent = buffer.toString().trim();
                chunks.add(createChunkDraft(chunkIndex++, currentPageFrom, currentPageTo, finalizedContent));

                String overlapPrefix = extractOverlapTail(finalizedContent);
                buffer.setLength(0);
                if (StringUtils.hasText(overlapPrefix)) {
                    buffer.append(overlapPrefix).append("\n\n");
                }
                buffer.append(segment.text());
                currentPageFrom = segment.pageNumber();
                currentPageTo = segment.pageNumber();
            }
        }

        if (!buffer.isEmpty()) {
            String remaining = buffer.toString().trim();
            if (StringUtils.hasText(remaining)) {
                chunks.add(createChunkDraft(chunkIndex, currentPageFrom, currentPageTo, remaining));
            }
        }

        return chunks;
    }

    private ExtractionResult extractPdfPages(Path filePath) throws IOException {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (Exception ex) {
                    log.warn("Could not remove PDF security restrictions: {}", ex.getMessage());
                }
            }

            int pageCount = document.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            List<PageText> pages = new ArrayList<>(pageCount);
            for (int pageNum = 1; pageNum <= pageCount; pageNum++) {
                stripper.setStartPage(pageNum);
                stripper.setEndPage(pageNum);
                String cleanedPage = cleanText(stripper.getText(document));
                if (StringUtils.hasText(cleanedPage)) {
                    pages.add(new PageText(pageNum, cleanedPage));
                }
            }
            return new ExtractionResult(pageCount, pages);
        }
    }

    private ExtractionResult extractDocx(Path filePath) throws IOException {
        try (InputStream is = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(is)) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (StringUtils.hasText(text)) {
                        sb.append(text).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    appendTableText(table, sb);
                }
            }

            Integer pageCount = null;
            try {
                if (document.getProperties() != null && document.getProperties().getExtendedProperties() != null) {
                    int pages = document.getProperties().getExtendedProperties().getUnderlyingProperties().getPages();
                    if (pages > 0) {
                        pageCount = pages;
                    }
                }
            } catch (Exception ignored) {
                pageCount = null;
            }

            String cleaned = cleanText(sb.toString());
            List<PageText> pages = StringUtils.hasText(cleaned) ? List.of(new PageText(null, cleaned)) : List.of();
            return new ExtractionResult(pageCount, pages);
        }
    }

    private ExtractionResult extractTxt(Path filePath) throws IOException {
        String raw;
        try {
            raw = Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            raw = Files.readString(filePath, StandardCharsets.ISO_8859_1);
        }
        String cleaned = cleanText(raw);
        List<PageText> pages = StringUtils.hasText(cleaned) ? List.of(new PageText(null, cleaned)) : List.of();
        return new ExtractionResult(null, pages);
    }

    private void appendTableText(XWPFTable table, StringBuilder sb) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cellValues = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                if (StringUtils.hasText(cellText)) {
                    cellValues.add(cellText.trim());
                }
            }
            if (!cellValues.isEmpty()) {
                sb.append(String.join(" | ", cellValues)).append("\n");
            }
        }
        sb.append("\n");
    }

    private List<TextSegment> toSegments(List<PageText> pages) {
        List<TextSegment> segments = new ArrayList<>();
        if (pages == null) {
            return segments;
        }
        for (PageText page : pages) {
            if (page == null || !StringUtils.hasText(page.text())) {
                continue;
            }
            String cleaned = cleanText(page.text());
            String[] paragraphs = cleaned.split("\\n\\n+");
            for (String paragraph : paragraphs) {
                String trimmed = paragraph.trim();
                if (!StringUtils.hasText(trimmed)) {
                    continue;
                }
                if (trimmed.length() <= MAX_CHUNK_SIZE) {
                    segments.add(new TextSegment(page.pageNumber(), trimmed));
                } else {
                    splitLongParagraph(page.pageNumber(), trimmed, segments);
                }
            }
        }
        return segments;
    }

    private void splitLongParagraph(Integer pageNumber, String paragraph, List<TextSegment> out) {
        int start = 0;
        int len = paragraph.length();
        while (start < len) {
            int maxEnd = Math.min(start + TARGET_CHUNK_SIZE, len);
            int splitAt = maxEnd;
            if (maxEnd < len) {
                int spaceIdx = paragraph.lastIndexOf(' ', maxEnd);
                if (spaceIdx > start + (MIN_CHUNK_SIZE / 2)) {
                    splitAt = spaceIdx;
                }
            }
            String piece = paragraph.substring(start, splitAt).trim();
            if (StringUtils.hasText(piece)) {
                out.add(new TextSegment(pageNumber, piece));
            }
            int nextStart = splitAt;
            while (nextStart < len && Character.isWhitespace(paragraph.charAt(nextStart))) {
                nextStart++;
            }
            if (nextStart <= start) {
                nextStart = Math.min(start + TARGET_CHUNK_SIZE, len);
            }
            start = nextStart;
        }
    }

    private String extractOverlapTail(String text) {
        if (text.length() <= CHUNK_OVERLAP) {
            return text;
        }
        int rawStart = text.length() - CHUNK_OVERLAP;
        int spaceIdx = text.indexOf(' ', rawStart);
        int start = (spaceIdx > rawStart && spaceIdx < text.length() - 1) ? spaceIdx + 1 : rawStart;
        return text.substring(start).trim();
    }

    private ChunkDraft createChunkDraft(int chunkIndex, Integer pageFrom, Integer pageTo, String content) {
        int estimatedTokens = Math.max(1, (int) Math.ceil(content.length() / 4.0));
        return new ChunkDraft(chunkIndex, pageFrom, pageTo, content, estimatedTokens);
    }

    private Integer maxPage(Integer current, Integer candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate == null) {
            return current;
        }
        return Math.max(current, candidate);
    }

    private MaterialMetadata loadMaterialForExtraction(Long materialId) {
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId).orElse(null);
        if (material == null) {
            log.debug("Skipping extraction for missing/deleted material id={}", materialId);
            return null;
        }
        if (material.getStatus() != MaterialStatus.EXTRACTING) {
            material.setStatus(MaterialStatus.EXTRACTING);
            material.setErrorCode(null);
            material.setErrorMessage(null);
            materialRepository.save(material);
        }
        return new MaterialMetadata(material.getId(), material.getStoredPath(), material.getFileName());
    }

    private void persistExtractedChunks(Long materialId, Integer pageCount, List<ChunkDraft> chunkDrafts) {
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId).orElse(null);
        if (material == null) {
            log.debug("Material id={} deleted before chunks could be persisted", materialId);
            return;
        }

        materialChunkRepository.deleteByMaterialId(materialId);
        materialChunkRepository.flush();

        List<MaterialChunk> entities = new ArrayList<>(chunkDrafts.size());
        for (ChunkDraft draft : chunkDrafts) {
            entities.add(MaterialChunk.builder()
                    .material(material)
                    .chunkIndex(draft.chunkIndex())
                    .pageFrom(draft.pageFrom())
                    .pageTo(draft.pageTo())
                    .content(draft.content())
                    .tokenCount(draft.tokenCount())
                    .build());
        }
        materialChunkRepository.saveAll(entities);

        material.setStatus(MaterialStatus.EXTRACTED);
        material.setPageCount(pageCount);
        material.setErrorCode(null);
        material.setErrorMessage(null);
        materialRepository.save(material);
    }

    private void markFailed(Long materialId, Integer pageCount, String errorCode, String errorMessage) {
        Material material = materialRepository.findByIdAndDeletedAtIsNull(materialId).orElse(null);
        if (material == null) {
            return;
        }
        materialChunkRepository.deleteByMaterialId(materialId);
        material.setStatus(MaterialStatus.FAILED);
        material.setPageCount(pageCount);
        material.setErrorCode(errorCode);
        material.setErrorMessage(errorMessage);
        materialRepository.save(material);
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private record TextSegment(Integer pageNumber, String text) {
    }

    private record MaterialMetadata(Long id, String storedPath, String fileName) {
    }
}
