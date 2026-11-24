package com.aistudy.assistant.dto.summary;

import com.aistudy.assistant.entity.LengthType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateSummaryRequest {

    @NotNull(message = "Material ID is required")
    private Long materialId;

    @NotNull(message = "Length type is required (SHORT, MEDIUM, DETAILED)")
    private LengthType lengthType;
}
