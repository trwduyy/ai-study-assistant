package com.aistudy.assistant.learning.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
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
import com.aistudy.assistant.learning.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST endpoints for Course, Chapter, and Topic management under V2 contract.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Courses, Chapters & Topics V2", description = "Quản lý khóa học, chương và chủ đề cá nhân")
@SecurityRequirement(name = "BearerAuth")
public class CourseController {

    private final CourseService courseService;

    @GetMapping("/courses")
    @Operation(summary = "Danh sách khóa học của tôi (phân trang)")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getCourses(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<CourseResponse> page = courseService.getCourses(pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PostMapping("/courses")
    @Operation(summary = "Tạo khóa học mới")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse response = courseService.createCourse(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @GetMapping("/courses/{courseId}")
    @Operation(summary = "Chi tiết khóa học kèm danh sách chương và chủ đề")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourseDetail(@PathVariable Long courseId) {
        CourseDetailResponse response = courseService.getCourseDetail(courseId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/courses/{courseId}")
    @Operation(summary = "Cập nhật khóa học (kiểm tra version hoặc header If-Match)")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable Long courseId,
            @Valid @RequestBody UpdateCourseRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        CourseResponse response = courseService.updateCourse(courseId, request, ifMatchVersion);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/courses/{courseId}")
    @Operation(summary = "Xóa mềm khóa học")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long courseId) {
        courseService.deleteCourse(courseId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/courses/{courseId}/chapters")
    @Operation(summary = "Danh sách chương trong khóa học")
    public ResponseEntity<ApiResponse<List<ChapterResponse>>> getChapters(@PathVariable Long courseId) {
        List<ChapterResponse> chapters = courseService.getChapters(courseId);
        return ResponseEntity.ok(ApiResponse.success(chapters));
    }

    @PostMapping("/courses/{courseId}/chapters")
    @Operation(summary = "Tạo chương mới trong khóa học")
    public ResponseEntity<ApiResponse<ChapterResponse>> createChapter(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateChapterRequest request) {
        ChapterResponse response = courseService.createChapter(courseId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/chapters/{chapterId}")
    @Operation(summary = "Cập nhật chương học")
    public ResponseEntity<ApiResponse<ChapterResponse>> updateChapter(
            @PathVariable Long chapterId,
            @Valid @RequestBody UpdateChapterRequest request) {
        ChapterResponse response = courseService.updateChapter(chapterId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/chapters/{chapterId}")
    @Operation(summary = "Xóa chương học")
    public ResponseEntity<ApiResponse<Void>> deleteChapter(@PathVariable Long chapterId) {
        courseService.deleteChapter(chapterId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/chapters/{chapterId}/topics")
    @Operation(summary = "Danh sách chủ đề trong chương")
    public ResponseEntity<ApiResponse<List<TopicResponse>>> getTopics(@PathVariable Long chapterId) {
        List<TopicResponse> topics = courseService.getTopics(chapterId);
        return ResponseEntity.ok(ApiResponse.success(topics));
    }

    @PostMapping("/chapters/{chapterId}/topics")
    @Operation(summary = "Tạo chủ đề mới trong chương")
    public ResponseEntity<ApiResponse<TopicResponse>> createTopic(
            @PathVariable Long chapterId,
            @Valid @RequestBody CreateTopicRequest request) {
        TopicResponse response = courseService.createTopic(chapterId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @PutMapping("/topics/{topicId}")
    @Operation(summary = "Cập nhật chủ đề")
    public ResponseEntity<ApiResponse<TopicResponse>> updateTopic(
            @PathVariable Long topicId,
            @Valid @RequestBody UpdateTopicRequest request) {
        TopicResponse response = courseService.updateTopic(topicId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/topics/{topicId}")
    @Operation(summary = "Xóa chủ đề")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(@PathVariable Long topicId) {
        courseService.deleteTopic(topicId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private Integer parseIfMatchVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            return null;
        }
        String sanitized = ifMatchHeader.trim();
        if (sanitized.startsWith("W/")) {
            sanitized = sanitized.substring(2).trim();
        }
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() >= 2) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        try {
            return Integer.parseInt(sanitized);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header If-Match không hợp lệ.");
        }
    }
}
