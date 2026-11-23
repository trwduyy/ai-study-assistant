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
public class MaterialResponse {

    private Long id;
    private Long courseId;
    private Long topicId;
    private String originalName;
    private String fileType;
    private Long fileSize;
    private ExtractionStatus extractionStatus;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static MaterialResponse fromEntity(Material material) {
        if (material == null) {
            return null;
        }

        return MaterialResponse.builder()
                .id(material.getId())
                .courseId(material.getCourse() != null ? material.getCourse().getId() : null)
                .topicId(material.getTopic() != null ? material.getTopic().getId() : null)
                .originalName(material.getOriginalName())
                .fileType(material.getFileType())
                .fileSize(material.getFileSize())
                .extractionStatus(material.getExtractionStatus())
                .createdAt(material.getCreatedAt())
                .build();
    }
}
