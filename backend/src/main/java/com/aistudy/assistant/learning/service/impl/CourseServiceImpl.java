package com.aistudy.assistant.learning.service.impl;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.learning.dto.ChapterDetailResponse;
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
import com.aistudy.assistant.learning.entity.Chapter;
import com.aistudy.assistant.learning.entity.Course;
import com.aistudy.assistant.learning.entity.Topic;
import com.aistudy.assistant.learning.mapper.LearningMapper;
import com.aistudy.assistant.learning.repository.ChapterRepository;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.TopicRepository;
import com.aistudy.assistant.learning.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Manages user-owned courses, chapters, and topics with soft deletion, optimistic locking, and N+1-free queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseRepository courseRepository;
    private final ChapterRepository chapterRepository;
    private final TopicRepository topicRepository;
    private final MaterialRepository materialRepository;
    private final UserRepository userRepository;
    private final LearningMapper learningMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<CourseResponse> getCourses(Pageable pageable) {
        Long currentUserId = SecurityUtils.currentUserId();
        return courseRepository.findProjectedByOwnerId(currentUserId, pageable);
    }

    @Override
    @Transactional
    public CourseResponse createCourse(CreateCourseRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        User owner = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Course course = Course.builder()
                .owner(owner)
                .name(request.getName().trim())
                .description(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null)
                .build();

        Course saved = courseRepository.save(course);
        log.info("Created course id={} for user id={}", saved.getId(), currentUserId);
        return learningMapper.toCourseResponse(saved, 0L, 0L);
    }

    @Override
    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(Long courseId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Course course = findActiveCourseAndVerifyOwner(courseId, currentUserId);

        List<Chapter> chapters = chapterRepository.findByCourseIdOrdered(courseId);
        Map<Long, List<TopicResponse>> topicsByChapter = loadTopicsGroupedByChapter(chapters);

        List<ChapterDetailResponse> chapterDetails = chapters.stream()
                .map(chapter -> learningMapper.toChapterDetailResponse(
                        chapter,
                        topicsByChapter.getOrDefault(chapter.getId(), Collections.emptyList())
                ))
                .toList();

        return learningMapper.toCourseDetailResponse(course, chapterDetails);
    }

    @Override
    @Transactional
    public CourseResponse updateCourse(Long courseId, UpdateCourseRequest request, Integer ifMatchVersion) {
        Long currentUserId = SecurityUtils.currentUserId();
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu thông tin version để cập nhật khóa học.",
                    List.of(new FieldErrorDetail("version", "Vui lòng cung cấp version trong body hoặc header If-Match"))
            );
        }

        Course course = findActiveCourseAndVerifyOwner(courseId, currentUserId);
        if (!Objects.equals(course.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        course.setName(request.getName().trim());
        course.setDescription(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null);

        Course updated = courseRepository.saveAndFlush(course);
        long chapterCount = chapterRepository.countByCourseId(courseId);
        long materialCount = materialRepository.countByCourseIdAndDeletedAtIsNull(courseId);

        log.info("Updated course id={}, newVersion={} by user id={}", courseId, updated.getVersion(), currentUserId);
        return learningMapper.toCourseResponse(updated, chapterCount, materialCount);
    }

    @Override
    @Transactional
    public void deleteCourse(Long courseId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Course course = findActiveCourseAndVerifyOwner(courseId, currentUserId);

        Instant now = Instant.now();
        course.setDeletedAt(now);
        courseRepository.save(course);
        materialRepository.softDeleteByCourseId(courseId, now);
        log.info("Soft-deleted course id={} by user id={}", courseId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChapterResponse> getChapters(Long courseId) {
        Long currentUserId = SecurityUtils.currentUserId();
        findActiveCourseAndVerifyOwner(courseId, currentUserId);

        return chapterRepository.findByCourseIdOrdered(courseId).stream()
                .map(learningMapper::toChapterResponse)
                .toList();
    }

    @Override
    @Transactional
    public ChapterResponse createChapter(Long courseId, CreateChapterRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Course course = findActiveCourseAndVerifyOwner(courseId, currentUserId);

        int position = request.getPosition() != null
                ? request.getPosition()
                : (int) chapterRepository.countByCourseId(courseId);

        Chapter chapter = Chapter.builder()
                .course(course)
                .title(request.getTitle().trim())
                .position(position)
                .build();

        Chapter saved = chapterRepository.save(chapter);
        log.info("Created chapter id={} in course id={}", saved.getId(), courseId);
        return learningMapper.toChapterResponse(saved);
    }

    @Override
    @Transactional
    public ChapterResponse updateChapter(Long chapterId, UpdateChapterRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Chapter chapter = findActiveChapterAndVerifyOwner(chapterId, currentUserId);

        chapter.setTitle(request.getTitle().trim());
        if (request.getPosition() != null) {
            chapter.setPosition(request.getPosition());
        }

        Chapter updated = chapterRepository.save(chapter);
        log.info("Updated chapter id={} by user id={}", chapterId, currentUserId);
        return learningMapper.toChapterResponse(updated);
    }

    @Override
    @Transactional
    public void deleteChapter(Long chapterId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Chapter chapter = findActiveChapterAndVerifyOwner(chapterId, currentUserId);

        materialRepository.clearTopicReferencesByChapterId(chapterId);
        List<Topic> topics = topicRepository.findByChapterIdOrdered(chapterId);
        if (!topics.isEmpty()) {
            topicRepository.deleteAllInBatch(topics);
        }
        chapterRepository.delete(chapter);
        log.info("Deleted chapter id={} by user id={}", chapterId, currentUserId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicResponse> getTopics(Long chapterId) {
        Long currentUserId = SecurityUtils.currentUserId();
        findActiveChapterAndVerifyOwner(chapterId, currentUserId);

        return topicRepository.findByChapterIdOrdered(chapterId).stream()
                .map(learningMapper::toTopicResponse)
                .toList();
    }

    @Override
    @Transactional
    public TopicResponse createTopic(Long chapterId, CreateTopicRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Chapter chapter = findActiveChapterAndVerifyOwner(chapterId, currentUserId);

        int position = request.getPosition() != null
                ? request.getPosition()
                : topicRepository.findByChapterIdOrdered(chapterId).size();

        Topic topic = Topic.builder()
                .chapter(chapter)
                .title(request.getTitle().trim())
                .position(position)
                .build();

        Topic saved = topicRepository.save(topic);
        log.info("Created topic id={} in chapter id={}", saved.getId(), chapterId);
        return learningMapper.toTopicResponse(saved);
    }

    @Override
    @Transactional
    public TopicResponse updateTopic(Long topicId, UpdateTopicRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Topic topic = findActiveTopicAndVerifyOwner(topicId, currentUserId);

        topic.setTitle(request.getTitle().trim());
        if (request.getPosition() != null) {
            topic.setPosition(request.getPosition());
        }

        Topic updated = topicRepository.save(topic);
        log.info("Updated topic id={} by user id={}", topicId, currentUserId);
        return learningMapper.toTopicResponse(updated);
    }

    @Override
    @Transactional
    public void deleteTopic(Long topicId) {
        Long currentUserId = SecurityUtils.currentUserId();
        Topic topic = findActiveTopicAndVerifyOwner(topicId, currentUserId);

        materialRepository.clearTopicReferences(topicId);
        topicRepository.delete(topic);
        log.info("Deleted topic id={} by user id={}", topicId, currentUserId);
    }

    private Course findActiveCourseAndVerifyOwner(Long courseId, Long userId) {
        Course course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new AppException(ErrorCode.COURSE_NOT_FOUND));
        if (!course.getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return course;
    }

    private Chapter findActiveChapterAndVerifyOwner(Long chapterId, Long userId) {
        Chapter chapter = chapterRepository.findWithCourseAndOwnerById(chapterId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAPTER_NOT_FOUND));
        if (!chapter.getCourse().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return chapter;
    }

    private Topic findActiveTopicAndVerifyOwner(Long topicId, Long userId) {
        Topic topic = topicRepository.findWithChapterCourseAndOwnerById(topicId)
                .orElseThrow(() -> new AppException(ErrorCode.TOPIC_NOT_FOUND));
        if (!topic.getChapter().getCourse().getOwner().getId().equals(userId)) {
            throw new AppException(ErrorCode.NOT_RESOURCE_OWNER);
        }
        return topic;
    }

    private Map<Long, List<TopicResponse>> loadTopicsGroupedByChapter(List<Chapter> chapters) {
        if (chapters.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();
        return topicRepository.findByChapterIdsOrdered(chapterIds).stream()
                .collect(Collectors.groupingBy(
                        topic -> topic.getChapter().getId(),
                        Collectors.mapping(learningMapper::toTopicResponse, Collectors.toList())
                ));
    }
}
