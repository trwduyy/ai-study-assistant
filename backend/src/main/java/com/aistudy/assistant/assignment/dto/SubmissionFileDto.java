package com.aistudy.assistant.assignment.dto;

/**
 * Uploaded submission file summary DTO.
 */
public record SubmissionFileDto(
        Long id,
        String fileName,
        Long sizeBytes
) {
}
