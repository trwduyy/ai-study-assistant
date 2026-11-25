package com.aistudy.assistant.service.impl;

import com.aistudy.assistant.common.SecurityUtils;
import com.aistudy.assistant.dto.course.CourseDetailResponse;
import com.aistudy.assistant.dto.course.CourseResponse;
import com.aistudy.assistant.dto.course.CreateCourseRequest;
import com.aistudy.assistant.dto.course.CreateTopicRequest;
import com.aistudy.assistant.dto.course.TopicResponse;
import com.aistudy.assistant.dto.course.UpdateCourseRequest;
import com.aistudy.assistant.dto.course.UpdateTopicRequest;
import com.aistudy.assistant.entity.Course;
import com.aistudy.assistant.entity.Topic;
import com.aistudy.assistant.entity.User;
import com.aistudy.assistant.exception.ResourceNotFoundException;
import com.aistudy.assistant.repository.CourseRepository;
import com.aistudy.assistant.repository.TopicRepository;
import com.aistudy.assistant.repository.UserRepository;
import com.aistudy.assistant.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CourseResponse> getUserCourses() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        List<Course> courses = courseRepository.findByUserIdOrderByCreatedAtDesc(currentUserId);
        if (courses.isEmpty()) {
            return List.of();
        }

        List<Long> courseIds = courses.stream()
                .map(Course::getId)
                .collect(Collectors.toList());

        Map<Long, Long> topicCountMap = toCountMap(courseRepository.countTopicsByCourseIds(courseIds));
        Map<Long, Long> materialCountMap = toCountMap(courseRepository.countMaterialsByCourseIds(courseIds));

        return courses.stream()
                .map(course -> CourseResponse.fromEntity(
                        course,
                        topicCountMap.getOrDefault(course.getId(), 0L),
                        materialCountMap.getOrDefault(course.getId(), 0L)
                ))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", currentUserId));

        Course course = Course.builder()
                .user(currentUser)
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .build();

        Course savedCourse = courseRepository.save(course);
        log.info("Course created successfully with id: {} by user: {}", savedCourse.getId(), currentUserId);

        return CourseResponse.fromEntity(savedCourse, 0L, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(Long courseId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Course course = courseRepository.findWithTopicsByIdAndUserId(courseId, currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));

        return CourseDetailResponse.fromEntity(course);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(Long courseId, UpdateCourseRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Course course = findCourseAndValidateOwnership(courseId, currentUserId);

        course.setTitle(request.getTitle().trim());
        course.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);

        Course updatedCourse = courseRepository.save(course);
        long topicCount = courseRepository.countTopicsByCourseId(courseId);
        long materialCount = courseRepository.countMaterialsByCourseId(courseId);

        log.info("Course {} updated successfully by user: {}", courseId, currentUserId);
        return CourseResponse.fromEntity(updatedCourse, topicCount, materialCount);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Course course = findCourseAndValidateOwnership(courseId, currentUserId);

        courseRepository.delete(course);
        log.info("Course {} deleted successfully by user: {}", courseId, currentUserId);
    }

    @Override
    @Transactional
    public TopicResponse createTopic(Long courseId, CreateTopicRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        Course course = findCourseAndValidateOwnership(courseId, currentUserId);

        int orderIndex = request.getOrderIndex() != null ? request.getOrderIndex() : 0;

        Topic topic = Topic.builder()
                .course(course)
                .title(request.getTitle().trim())
                .orderIndex(orderIndex)
                .build();

        Topic savedTopic = topicRepository.save(topic);
        log.info("Topic {} created in course: {}", savedTopic.getId(), courseId);

        return TopicResponse.fromEntity(savedTopic);
    }

    @Override
    @Transactional
    public TopicResponse updateTopic(Long courseId, Long topicId, UpdateTopicRequest request) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        validateCourseOwnership(courseId, currentUserId);

        Topic topic = topicRepository.findByIdAndCourseId(topicId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topic.setTitle(request.getTitle().trim());
        if (request.getOrderIndex() != null) {
            topic.setOrderIndex(request.getOrderIndex());
        }

        Topic updatedTopic = topicRepository.save(topic);
        log.info("Topic {} updated successfully in course: {}", topicId, courseId);

        return TopicResponse.fromEntity(updatedTopic);
    }

    @Override
    @Transactional
    public void deleteTopic(Long courseId, Long topicId) {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        validateCourseOwnership(courseId, currentUserId);

        Topic topic = topicRepository.findByIdAndCourseId(topicId, courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Topic", "id", topicId));

        topicRepository.delete(topic);
        log.info("Topic {} deleted successfully from course: {}", topicId, courseId);
    }

    private Course findCourseAndValidateOwnership(Long courseId, Long userId) {
        return courseRepository.findByIdAndUserId(courseId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Course", "id", courseId));
    }

    private void validateCourseOwnership(Long courseId, Long userId) {
        if (!courseRepository.existsByIdAndUserId(courseId, userId)) {
            throw new ResourceNotFoundException("Course", "id", courseId);
        }
    }

    private Map<Long, Long> toCountMap(List<CourseRepository.CourseCountProjection> projections) {
        return projections.stream()
                .collect(Collectors.toMap(
                        CourseRepository.CourseCountProjection::getCourseId,
                        CourseRepository.CourseCountProjection::getTotal,
                        Long::sum
                ));
    }
}
