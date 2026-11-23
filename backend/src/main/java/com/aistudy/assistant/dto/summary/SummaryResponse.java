package com.aistudy.assistant.dto.summary;

import com.aistudy.assistant.entity.LengthType;
import com.aistudy.assistant.entity.Summary;
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
public class SummaryResponse {

    private Long id;
    private Long materialId;
    private LengthType lengthType;
    private String content;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static SummaryResponse fromEntity(Summary summary) {
        if (summary == null) {
            return null;
        }

        return SummaryResponse.builder()
                .id(summary.getId())
                .materialId(summary.getMaterial() != null ? summary.getMaterial().getId() : null)
                .lengthType(summary.getLengthType())
                .content(summary.getContent())
                .createdAt(summary.getCreatedAt())
                .build();
    }
}
