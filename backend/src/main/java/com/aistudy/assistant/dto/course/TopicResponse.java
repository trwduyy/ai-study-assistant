package com.aistudy.assistant.dto.course;

import com.aistudy.assistant.entity.Topic;
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
public class TopicResponse {

    private Long id;
    private Long courseId;
    private String title;
    private Integer orderIndex;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    public static TopicResponse fromEntity(Topic topic) {
        if (topic == null) {
            return null;
        }

        return TopicResponse.builder()
                .id(topic.getId())
                .courseId(topic.getCourse() != null ? topic.getCourse().getId() : null)
                .title(topic.getTitle())
                .orderIndex(topic.getOrderIndex())
                .createdAt(topic.getCreatedAt())
                .build();
    }
}
