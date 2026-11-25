package com.aistudy.assistant.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Projected classroom shared material DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassMaterialResponse {

    private Long id;
    private Long classId;
    private Long uploaderId;
    private String uploaderName;
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private Instant createdAt;
    private Instant updatedAt;
}
