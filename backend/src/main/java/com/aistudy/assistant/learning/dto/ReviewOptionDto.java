package com.aistudy.assistant.learning.dto;

public record ReviewOptionDto(
        Long id,
        String label,
        String content,
        boolean correct
) {
}
