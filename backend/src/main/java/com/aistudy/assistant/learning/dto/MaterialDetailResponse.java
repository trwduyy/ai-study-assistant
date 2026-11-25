package com.aistudy.assistant.learning.dto;

import com.aistudy.assistant.learning.entity.MaterialStatus;
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
    private String fileName;
    private String mimeType;
    private Long sizeBytes;
    private String category;
    private MaterialStatus status;
    private Integer pageCount;
    private long chunkCount;
    private String contentHash;
    private String errorCode;
    private String errorMessage;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant updatedAt;
}
