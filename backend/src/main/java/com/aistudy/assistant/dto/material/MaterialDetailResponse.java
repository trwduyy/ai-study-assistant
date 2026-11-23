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
public class MaterialDetailResponse {

    private Long id;
    private Long courseId;
    private Long topicId;
    private String originalName;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String extractedText;
    private ExtractionStatus extractionStatus;
    private String errorMessage;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static MaterialDetailResponse fromEntity(Material material) {
        if (material == null) {
            return null;
        }

        return MaterialDetailResponse.builder()
                .id(material.getId())
                .courseId(material.getCourse() != null ? material.getCourse().getId() : null)
                .topicId(material.getTopic() != null ? material.getTopic().getId() : null)
                .originalName(material.getOriginalName())
                .fileName(material.getFileName())
                .fileType(material.getFileType())
                .fileSize(material.getFileSize())
                .extractedText(material.getExtractedText())
                .extractionStatus(material.getExtractionStatus())
                .errorMessage(material.getErrorMessage())
                .createdAt(material.getCreatedAt())
                .build();
    }
}
