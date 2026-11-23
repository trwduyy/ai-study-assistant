package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.course.CourseDetailResponse;
import com.aistudy.assistant.dto.course.CourseResponse;
import com.aistudy.assistant.dto.course.CreateCourseRequest;
import com.aistudy.assistant.dto.course.CreateTopicRequest;
import com.aistudy.assistant.dto.course.TopicResponse;
import com.aistudy.assistant.dto.course.UpdateCourseRequest;
import com.aistudy.assistant.dto.course.UpdateTopicRequest;

import java.util.List;

public interface CourseService {

    List<CourseResponse> getUserCourses();

    CourseResponse createCourse(CreateCourseRequest request);

    CourseDetailResponse getCourseDetail(Long courseId);

    CourseResponse updateCourse(Long courseId, UpdateCourseRequest request);

    void deleteCourse(Long courseId);

    TopicResponse createTopic(Long courseId, CreateTopicRequest request);

    TopicResponse updateTopic(Long courseId, Long topicId, UpdateTopicRequest request);

    void deleteTopic(Long courseId, Long topicId);
}
