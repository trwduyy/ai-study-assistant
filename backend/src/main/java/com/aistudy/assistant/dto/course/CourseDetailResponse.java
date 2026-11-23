package com.aistudy.assistant.dto.course;

import com.aistudy.assistant.entity.Course;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseDetailResponse {

    private Long id;
    private String title;
    private String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    private Instant updatedAt;

    @Builder.Default
    private List<TopicResponse> topics = new ArrayList<>();

    public static CourseDetailResponse fromEntity(Course course) {
        if (course == null) {
            return null;
        }

        List<TopicResponse> topicResponses = course.getTopics() != null
                ? course.getTopics().stream()
                .map(TopicResponse::fromEntity)
                .collect(Collectors.toList())
                : new ArrayList<>();

        return CourseDetailResponse.builder()
                .id(course.getId())
                .title(course.getTitle())
                .description(course.getDescription())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .topics(topicResponses)
                .build();
    }
}
