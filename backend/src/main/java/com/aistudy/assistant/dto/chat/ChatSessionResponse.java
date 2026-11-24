package com.aistudy.assistant.dto.chat;

import com.aistudy.assistant.entity.ChatSession;
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
public class ChatSessionResponse {

    private Long id;
    private String title;
    private Long courseId;
    private Long materialId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant updatedAt;

    public static ChatSessionResponse fromEntity(ChatSession session) {
        if (session == null) {
            return null;
        }

        return ChatSessionResponse.builder()
                .id(session.getId())
                .title(session.getTitle())
                .courseId(session.getCourse() != null ? session.getCourse().getId() : null)
                .materialId(session.getMaterial() != null ? session.getMaterial().getId() : null)
                .createdAt(session.getCreatedAt())
                .updatedAt(session.getUpdatedAt())
                .build();
    }
}
