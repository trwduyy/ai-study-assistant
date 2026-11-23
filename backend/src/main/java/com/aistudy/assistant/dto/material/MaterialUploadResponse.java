package com.aistudy.assistant.dto.material;

import com.aistudy.assistant.entity.ExtractionStatus;
import com.aistudy.assistant.entity.Material;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialUploadResponse {

    private Long id;
    private Long courseId;
    private Long topicId;
    private String fileName;
    private String originalName;
    private String fileType;
    private Long fileSize;
    private ExtractionStatus extractionStatus;
    private int extractedTextLength;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static MaterialUploadResponse fromEntity(Material material) {
        if (material == null) {
            return null;
        }

        int textLen = material.getExtractedText() != null ? material.getExtractedText().length() : 0;

        return MaterialUploadResponse.builder()
                .id(material.getId())
                .courseId(material.getCourse() != null ? material.getCourse().getId() : null)
                .topicId(material.getTopic() != null ? material.getTopic().getId() : null)
                .fileName(material.getFileName())
                .originalName(material.getOriginalName())
                .fileType(material.getFileType())
                .fileSize(material.getFileSize())
                .extractionStatus(material.getExtractionStatus())
                .extractedTextLength(textLen)
                .createdAt(material.getCreatedAt())
                .build();
    }
}
