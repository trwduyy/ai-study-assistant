package com.aistudy.assistant.learning.dto;

public record ReviewTopicResponse(
        Long courseId,
        String courseName,
        Long topicId,
        String topicTitle,
        double accuracyRate,
        long sampleSize,
        String confidence,
        String reason,
        Long suggestedMaterialId,
        String suggestedAction
) {
}
