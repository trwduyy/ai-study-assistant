package com.aistudy.assistant.learning.mapper;

import com.aistudy.assistant.learning.dto.ChapterDetailResponse;
import com.aistudy.assistant.learning.dto.ChapterResponse;
import com.aistudy.assistant.learning.dto.CourseDetailResponse;
import com.aistudy.assistant.learning.dto.CourseResponse;
import com.aistudy.assistant.learning.dto.MaterialDetailResponse;
import com.aistudy.assistant.learning.dto.MaterialResponse;
import com.aistudy.assistant.learning.dto.TopicResponse;
import com.aistudy.assistant.learning.entity.Chapter;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Material;
import com.aistudy.assistant.learning.entity.Topic;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps {@code learning} module entities to API response DTOs.
 */
@Component
public class LearningMapper {

    public CourseResponse toCourseResponse(Course course, long chapterCount, long materialCount) {
        return CourseResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .description(course.getDescription())
                .chapterCount(chapterCount)
                .materialCount(materialCount)
                .version(course.getVersion())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .build();
    }

    public CourseDetailResponse toCourseDetailResponse(Course course, List<ChapterDetailResponse> chapters) {
        return CourseDetailResponse.builder()
                .id(course.getId())
                .name(course.getName())
                .description(course.getDescription())
                .version(course.getVersion())
                .createdAt(course.getCreatedAt())
                .updatedAt(course.getUpdatedAt())
                .chapters(chapters)
                .build();
    }

    public ChapterResponse toChapterResponse(Chapter chapter) {
        return ChapterResponse.builder()
                .id(chapter.getId())
                .courseId(chapter.getCourse().getId())
                .title(chapter.getTitle())
                .position(chapter.getPosition())
                .createdAt(chapter.getCreatedAt())
                .updatedAt(chapter.getUpdatedAt())
                .build();
    }

    public ChapterDetailResponse toChapterDetailResponse(Chapter chapter, List<TopicResponse> topics) {
        return ChapterDetailResponse.builder()
                .id(chapter.getId())
                .courseId(chapter.getCourse().getId())
                .title(chapter.getTitle())
                .position(chapter.getPosition())
                .topics(topics)
                .build();
    }

    public TopicResponse toTopicResponse(Topic topic) {
        return TopicResponse.builder()
                .id(topic.getId())
                .chapterId(topic.getChapter().getId())
                .title(topic.getTitle())
                .position(topic.getPosition())
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .build();
    }

    public MaterialResponse toMaterialResponse(Material material) {
        return MaterialResponse.builder()
                .id(material.getId())
                .courseId(material.getCourse().getId())
                .topicId(material.getTopic() != null ? material.getTopic().getId() : null)
                .fileName(material.getFileName())
                .mimeType(material.getMimeType())
                .sizeBytes(material.getSizeBytes())
                .category(material.getCategory())
                .status(material.getStatus())
                .pageCount(material.getPageCount())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }

    public MaterialDetailResponse toMaterialDetailResponse(Material material, long chunkCount) {
        return MaterialDetailResponse.builder()
                .id(material.getId())
                .courseId(material.getCourse().getId())
                .topicId(material.getTopic() != null ? material.getTopic().getId() : null)
                .fileName(material.getFileName())
                .mimeType(material.getMimeType())
                .sizeBytes(material.getSizeBytes())
                .category(material.getCategory())
                .status(material.getStatus())
                .pageCount(material.getPageCount())
                .chunkCount(chunkCount)
                .contentHash(material.getContentHash())
                .errorCode(material.getErrorCode())
                .errorMessage(material.getErrorMessage())
                .createdAt(material.getCreatedAt())
                .updatedAt(material.getUpdatedAt())
                .build();
    }
}
