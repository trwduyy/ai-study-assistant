package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.UnprocessableEntityException;
import com.aistudy.assistant.service.TextExtractionService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

@Slf4j
@Service
public class TextExtractionServiceImpl implements TextExtractionService {

    private static final int MIN_EXTRACTABLE_TEXT_LENGTH = 50;
    private static final String SCANNED_PDF_ERROR_MESSAGE =
            "Document contains less than 50 characters of extractable text. Scanned or image-only documents are not supported in MVP.";

    @Override
    public String extractAndCleanText(Path filePath, String originalName) {
        String extension = getFileExtension(originalName).toLowerCase(Locale.ROOT);
        String rawText;

        try {
            rawText = switch (extension) {
                case "pdf" -> extractFromPdf(filePath);
                case "docx" -> extractFromDocx(filePath);
                case "txt" -> extractFromTxt(filePath);
                default -> throw new BadRequestException("Unsupported file type for extraction: " + extension);
            };
        } catch (Exception ex) {
            log.error("Failed to extract text from file {}: {}", originalName, ex.getMessage(), ex);
            throw new BadRequestException("Could not extract text from document: " + ex.getMessage());
        }

        String cleanedText = cleanText(rawText);

        if (!StringUtils.hasText(cleanedText) || cleanedText.length() < MIN_EXTRACTABLE_TEXT_LENGTH) {
            log.warn("Extracted text for {} is too short ({} chars), likely scanned document",
                    originalName, cleanedText != null ? cleanedText.length() : 0);
            throw new UnprocessableEntityException(SCANNED_PDF_ERROR_MESSAGE);
        }

        log.info("Successfully extracted {} characters from {}", cleanedText.length(), originalName);
        return cleanedText;
    }

    @Override
    public String cleanText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }

        // Standardize newlines
        String text = rawText.replace("\r\n", "\n").replace("\r", "\n");

        // Remove non-printable control characters except \n and \t
        text = text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");

        // Collapse multiple tabs and spaces into single space per line
        text = text.replaceAll("[ \\t]+", " ");

        // Trim whitespace from lines
        text = text.replaceAll("(?m)^[ \\t]+|[ \\t]+$", "");

        // Collapse 3 or more consecutive newlines into 2 newlines (preserve paragraph separation)
        text = text.replaceAll("\\n{3,}", "\n\n");

        return text.trim();
    }

    private String extractFromPdf(Path filePath) throws Exception {
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            if (document.isEncrypted()) {
                try {
                    document.setAllSecurityToBeRemoved(true);
                } catch (Exception e) {
                    log.warn("PDF is encrypted and could not remove security automatically: {}", e.getMessage());
                }
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String extractFromDocx(Path filePath) throws Exception {
        try (InputStream fis = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(fis)) {

            StringBuilder sb = new StringBuilder();

            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (StringUtils.hasText(text)) {
                        sb.append(text).append("\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    for (XWPFTableRow row : table.getRows()) {
                        StringBuilder rowBuilder = new StringBuilder();
                        for (XWPFTableCell cell : row.getTableCells()) {
                            String cellText = cell.getText();
                            if (StringUtils.hasText(cellText)) {
                                rowBuilder.append(cellText.trim()).append(" | ");
                            }
                        }
                        if (!rowBuilder.isEmpty()) {
                            sb.append(rowBuilder).append("\n");
                        }
                    }
                    sb.append("\n");
                }
            }

            return sb.toString();
        }
    }

    private String extractFromTxt(Path filePath) throws Exception {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (MalformedInputException ex) {
            log.info("UTF-8 reading failed for {}, falling back to ISO-8859-1", filePath.getFileName());
            return Files.readString(filePath, StandardCharsets.ISO_8859_1);
        }
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }
}
