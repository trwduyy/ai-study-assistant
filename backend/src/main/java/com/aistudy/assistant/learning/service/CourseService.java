package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.learning.dto.ChapterResponse;
import com.aistudy.assistant.learning.dto.CourseDetailResponse;
import com.aistudy.assistant.learning.dto.CourseResponse;
import com.aistudy.assistant.learning.dto.CreateChapterRequest;
import com.aistudy.assistant.learning.dto.CreateCourseRequest;
import com.aistudy.assistant.learning.dto.CreateTopicRequest;
import com.aistudy.assistant.learning.dto.TopicResponse;
import com.aistudy.assistant.learning.dto.UpdateChapterRequest;
import com.aistudy.assistant.learning.dto.UpdateCourseRequest;
import com.aistudy.assistant.learning.dto.UpdateTopicRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CourseService {

    Page<CourseResponse> getCourses(Pageable pageable);

    CourseResponse createCourse(CreateCourseRequest request);

    CourseDetailResponse getCourseDetail(Long courseId);

    CourseResponse updateCourse(Long courseId, UpdateCourseRequest request, Integer ifMatchVersion);

    void deleteCourse(Long courseId);

    List<ChapterResponse> getChapters(Long courseId);

    ChapterResponse createChapter(Long courseId, CreateChapterRequest request);

    ChapterResponse updateChapter(Long chapterId, UpdateChapterRequest request);

    void deleteChapter(Long chapterId);

    List<TopicResponse> getTopics(Long chapterId);

    TopicResponse createTopic(Long chapterId, CreateTopicRequest request);

    TopicResponse updateTopic(Long topicId, UpdateTopicRequest request);

    void deleteTopic(Long topicId);
}
