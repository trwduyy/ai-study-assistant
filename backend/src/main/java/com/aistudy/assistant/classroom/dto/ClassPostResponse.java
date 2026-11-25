package com.aistudy.assistant.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Projected classroom post DTO for announcements feed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassPostResponse {

    private Long id;
    private Long classId;
    private Long authorId;
    private String authorName;
    private String title;
    private String content;
    private Boolean pinned;
    private Instant createdAt;
    private Instant updatedAt;
}
