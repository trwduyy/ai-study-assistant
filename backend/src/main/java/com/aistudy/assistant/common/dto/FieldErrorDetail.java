package com.aistudy.assistant.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Field-level validation error entry included in {@link ErrorDetail}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldErrorDetail {

    private String field;
    private String message;
}
