package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.ai.service.EmbeddingService;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.MaterialStatus;
import com.aistudy.assistant.learning.repository.MaterialChunkRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.service.impl.FileProcessingServiceImpl;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileProcessingServiceTest {

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialChunkRepository materialChunkRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private EmbeddingService embeddingService;

    private FileProcessingServiceImpl fileProcessingService;

    @BeforeEach
    void setUp() {
        TransactionTemplate immediateTxTemplate = new TransactionTemplate() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }

            @Override
            public void executeWithoutResult(Consumer<TransactionStatus> action) {
                action.accept(new SimpleTransactionStatus());
            }
        };

        fileProcessingService = new FileProcessingServiceImpl(
                materialRepository,
                materialChunkRepository,
                fileStorageService,
                embeddingService,
                immediateTxTemplate
        );
    }

    @Test
    @DisplayName("Should build page-aware chunks preserving pageFrom/pageTo, overlap, and token estimates")
    void buildPageAwareChunks_PreservesPageRanges() {
        String p1 = "Page one discusses TCP flow control and sliding windows. ".repeat(14).trim();
        String p2 = "Page two explains congestion avoidance and slow start phases. ".repeat(14).trim();
        String p3 = "Page three summarizes UDP datagrams and checksum verification. ".repeat(14).trim();

        List<FileProcessingService.ChunkDraft> chunks = fileProcessingService.buildPageAwareChunks(List.of(
                new FileProcessingService.PageText(1, p1),
                new FileProcessingService.PageText(2, p2),
                new FileProcessingService.PageText(3, p3)
        ));

        assertTrue(chunks.size() >= 2);
        assertEquals(0, chunks.get(0).chunkIndex());
        assertEquals(1, chunks.get(0).pageFrom());
        for (int i = 0; i < chunks.size(); i++) {
            FileProcessingService.ChunkDraft chunk = chunks.get(i);
            assertEquals(i, chunk.chunkIndex());
            assertNotNull(chunk.pageFrom());
            assertNotNull(chunk.pageTo());
            assertTrue(chunk.pageFrom() <= chunk.pageTo());
            assertTrue(chunk.tokenCount() > 0);
            assertTrue(chunk.content().length() <= FileProcessingServiceImpl.MAX_CHUNK_SIZE + 200);
        }
        assertEquals(3, chunks.get(chunks.size() - 1).pageTo());
    }

    @Test
    @DisplayName("Should extract multi-page PDF accurately with PDFBox and clean control characters")
    void extractPagesFromFile_MultiPagePdf_PreservesPageCountAndText(@TempDir Path tempDir) throws IOException {
        Path pdfPath = tempDir.resolve("network_notes.pdf");
        try (PDDocument doc = new PDDocument()) {
            addPdfPage(doc, "Chapter 1: Transport Layer covers reliable byte stream delivery over IP networks.");
            addPdfPage(doc, "Chapter 2: Congestion Control uses cwnd and ssthresh thresholds dynamically.");
            doc.save(pdfPath.toFile());
        }

        FileProcessingService.ExtractionResult result = fileProcessingService.extractPagesFromFile(pdfPath, "network_notes.pdf");

        assertEquals(2, result.pageCount());
        assertEquals(2, result.pages().size());
        assertEquals(1, result.pages().get(0).pageNumber());
        assertTrue(result.pages().get(0).text().contains("Transport Layer"));
        assertEquals(2, result.pages().get(1).pageNumber());
        assertTrue(result.pages().get(1).text().contains("Congestion Control"));
    }

    @Test
    @DisplayName("Should mark material FAILED when extracted text is shorter than 50 characters")
    void extractAsync_TooShortOrEmptyFile_MarksMaterialFailed(@TempDir Path tempDir) throws IOException {
        Path shortTxt = tempDir.resolve("scan_stub.txt");
        Files.writeString(shortTxt, "Too short.");

        Material material = Material.builder()
                .id(77L)
                .fileName("scan_stub.txt")
                .storedPath("1/scan_stub.txt")
                .mimeType("text/plain")
                .sizeBytes(10L)
                .status(MaterialStatus.EXTRACTING)
                .build();

        when(materialRepository.findByIdAndDeletedAtIsNull(77L)).thenReturn(Optional.of(material));
        when(fileStorageService.resolvePath("1/scan_stub.txt")).thenReturn(shortTxt);
        lenient().when(materialRepository.save(any(Material.class))).thenAnswer(inv -> inv.getArgument(0));

        fileProcessingService.extractAsync(77L);

        assertEquals(MaterialStatus.FAILED, material.getStatus());
        assertEquals(ErrorCode.SCANNED_DOCUMENT_UNSUPPORTED.name(), material.getErrorCode());
        verify(materialChunkRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Should clean whitespace and control characters while preserving paragraph breaks")
    void cleanText_NormalizesFormatting() {
        String dirty = "Line 1   \t  word.\u0000\u0007\r\n\r\n\r\nLine 2 here.  ";
        String cleaned = fileProcessingService.cleanText(dirty);

        assertFalse(cleaned.contains("\u0000"));
        assertEquals("Line 1 word.\n\nLine 2 here.", cleaned);
    }

    private void addPdfPage(PDDocument doc, String text) throws IOException {
        PDPage page = new PDPage();
        doc.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(doc, page)) {
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 12);
            contentStream.newLineAtOffset(50, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
    }
}
