package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.common.SecurityUtils;
import com.aistudy.assistant.dto.summary.GenerateSummaryRequest;
import com.aistudy.assistant.dto.summary.SummaryResponse;
import com.aistudy.assistant.entity.ExtractionStatus;
import com.aistudy.assistant.entity.LengthType;
import com.aistudy.assistant.entity.Material;
import com.aistudy.assistant.entity.Summary;
import com.aistudy.assistant.exception.BadRequestException;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.MaterialRepository;
import com.aistudy.assistant.repository.SummaryRepository;
import com.aistudy.assistant.service.GeminiClientService;
import com.aistudy.assistant.service.SummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private static final int CHUNK_SIZE = 8000;
    private static final int CHUNK_OVERLAP = 500;
    private static final int MAX_SINGLE_PASS_LENGTH = 12000;

    private final MaterialRepository materialRepository;
    private final SummaryRepository summaryRepository;
    private final GeminiClientService geminiClientService;

    @Override
    @Transactional
    public SummaryResponse generateSummary(GenerateSummaryRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Material material = materialRepository.findByIdAndUserId(request.getMaterialId(), currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Material", "id", request.getMaterialId()));

        if (material.getExtractionStatus() != ExtractionStatus.SUCCESS || !StringUtils.hasText(material.getExtractedText())) {
            throw new BadRequestException("Cannot generate summary: Document text extraction has not succeeded or is empty.");
        }

        String documentText = material.getExtractedText();
        log.info("Generating summary for material {} ({} chars, lengthType={})",
                material.getId(), documentText.length(), request.getLengthType());

        String contentToSummarize;
        if (documentText.length() > MAX_SINGLE_PASS_LENGTH) {
            contentToSummarize = processLongDocumentChunks(documentText, material.getOriginalName());
        } else {
            contentToSummarize = documentText;
        }

        String systemPrompt = """
                Bạn là một trợ lý gia sư học tập AI thông minh, tận tâm và chính xác.
                Nhiệm vụ của bạn là tóm tắt tài liệu học tập một cách dễ hiểu, có cấu trúc rõ ràng, giữ trọn vẹn thuật ngữ chuyên môn và công thức quan trọng.
                Luôn phản hồi bằng tiếng Việt chuẩn mực, định dạng Markdown đẹp mắt.
                """;

        String userPrompt = buildSummaryPrompt(material.getOriginalName(), contentToSummarize, request.getLengthType());
        String summaryContent = geminiClientService.generateText(systemPrompt, userPrompt);

        Summary summary = Summary.builder()
                .material(material)
                .user(material.getUser())
                .lengthType(request.getLengthType())
                .content(summaryContent)
                .build();

        Summary savedSummary = summaryRepository.save(summary);
        log.info("Summary {} created successfully for material: {}", savedSummary.getId(), material.getId());

        return SummaryResponse.fromEntity(savedSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SummaryResponse> getSummariesByMaterial(Long materialId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        if (!materialRepository.existsByIdAndUserId(materialId, currentUserId)) {
            throw new ResourceNotFoundException("Material", "id", materialId);
        }

        List<Summary> summaries = summaryRepository.findByMaterialIdAndUserIdOrderByCreatedAtDesc(materialId, currentUserId);
        return summaries.stream()
                .map(SummaryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public SummaryResponse getSummaryById(Long id) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Summary summary = summaryRepository.findByIdAndUserId(id, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Summary", "id", id));

        return SummaryResponse.fromEntity(summary);
    }

    private String buildSummaryPrompt(String documentName, String text, LengthType lengthType) {
        String lengthInstructions = switch (lengthType) {
            case SHORT -> """
                    Định dạng yêu cầu:
                    - Tóm tắt cực kỳ ngắn gọn và súc tích trong 3 đến 5 gạch đầu dòng cốt lõi nhất.
                    - Nêu bật thông điệp chính và kết luận quan trọng nhất của tài liệu.
                    """;
            case MEDIUM -> """
                    Định dạng yêu cầu:
                    - Tóm tắt có cấu trúc chuẩn mực với các đề mục Markdown:
                      ### Khái niệm cốt lõi (Key Concepts)
                      ### Nội dung & luận điểm trọng tâm
                      ### Công thức / Quy tắc quan trọng (nếu có)
                      ### Tóm lược tổng quát
                    - Độ dài khoảng 3 - 5 đoạn văn cân đối, rõ ý.
                    """;
            case DETAILED -> """
                    Định dạng yêu cầu:
                    - Tóm tắt chi tiết, chuyên sâu và toàn diện:
                      ### Tổng quan nội dung
                      ### Phân tích chi tiết từng luận điểm & cơ chế hoạt động
                      ### Các công thức, định lý, ví dụ minh họa nổi bật (sử dụng KaTeX nếu có toán học: $...$ hoặc $$...$$)
                      ### Kết luận & các lưu ý quan trọng khi ôn tập
                    - Đảm bảo không bỏ sót bất kỳ chi tiết học tập quan trọng nào.
                    """;
        };

        return String.format("""
                Dưới đây là nội dung trích xuất từ tài liệu: '%s'

                --- BẮT ĐẦU TÀI LIỆU ---
                %s
                --- KẾT THÚC TÀI LIỆU ---

                %s
                """, documentName, text, lengthInstructions);
    }

    private String processLongDocumentChunks(String text, String documentName) {
        log.info("Document is long ({} chars), chunking and summarizing in multiple passes", text.length());
        List<String> chunks = createChunks(text, CHUNK_SIZE, CHUNK_OVERLAP);
        StringBuilder intermediateSummaries = new StringBuilder();

        String systemPrompt = "Bạn là trợ lý học tập AI. Hãy tóm tắt trung thực, cô đọng nội dung phân đoạn sau của tài liệu bằng tiếng Việt.";

        for (int i = 0; i < chunks.size(); i++) {
            String chunkPrompt = String.format(
                    "Tóm tắt các ý chính của phân đoạn %d/%d tài liệu '%s':\n\n%s",
                    i + 1, chunks.size(), documentName, chunks.get(i)
            );
            String chunkSummary = geminiClientService.generateText(systemPrompt, chunkPrompt);
            intermediateSummaries.append("Phân đoạn ").append(i + 1).append(":\n").append(chunkSummary).append("\n\n");
        }

        return intermediateSummaries.toString();
    }

    private List<String> createChunks(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = text.length();

        while (start < length) {
            int end = Math.min(start + chunkSize, length);
            chunks.add(text.substring(start, end));
            if (end == length) {
                break;
            }
            start = end - overlap;
        }

        return chunks;
    }
}
