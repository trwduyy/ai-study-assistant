package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.course.CourseDetailResponse;
import com.aistudy.assistant.dto.course.CourseResponse;
import com.aistudy.assistant.dto.course.CreateCourseRequest;
import com.aistudy.assistant.dto.course.CreateTopicRequest;
import com.aistudy.assistant.dto.course.TopicResponse;
import com.aistudy.assistant.dto.course.UpdateCourseRequest;
import com.aistudy.assistant.dto.course.UpdateTopicRequest;
import com.aistudy.assistant.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@Tag(name = "Courses & Topics", description = "Endpoints for managing study courses and their topics")
@SecurityRequirement(name = "BearerAuth")
public class CourseController {

    private final CourseService courseService;

    @GetMapping
    @Operation(summary = "List all courses belonging to authenticated user")
    public ResponseEntity<ApiResponse<List<CourseResponse>>> getUserCourses() {
        List<CourseResponse> courses = courseService.getUserCourses();
        return ResponseEntity.ok(ApiResponse.success(courses, "Courses retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create a new course")
    public ResponseEntity<ApiResponse<CourseResponse>> createCourse(@Valid @RequestBody CreateCourseRequest request) {
        CourseResponse response = courseService.createCourse(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Course created successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed information of a course including its topics")
    public ResponseEntity<ApiResponse<CourseDetailResponse>> getCourseDetail(@PathVariable Long id) {
        CourseDetailResponse response = courseService.getCourseDetail(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Course details retrieved"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing course")
    public ResponseEntity<ApiResponse<CourseResponse>> updateCourse(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCourseRequest request) {
        CourseResponse response = courseService.updateCourse(id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Course updated successfully"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a course and all associated topics and materials")
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long id) {
        courseService.deleteCourse(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Course deleted successfully"));
    }

    @PostMapping("/{courseId}/topics")
    @Operation(summary = "Create a new topic in a course")
    public ResponseEntity<ApiResponse<TopicResponse>> createTopic(
            @PathVariable Long courseId,
            @Valid @RequestBody CreateTopicRequest request) {
        TopicResponse response = courseService.createTopic(courseId, request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Topic created successfully"));
    }

    @PutMapping("/{courseId}/topics/{topicId}")
    @Operation(summary = "Update an existing topic in a course")
    public ResponseEntity<ApiResponse<TopicResponse>> updateTopic(
            @PathVariable Long courseId,
            @PathVariable Long topicId,
            @Valid @RequestBody UpdateTopicRequest request) {
        TopicResponse response = courseService.updateTopic(courseId, topicId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Topic updated successfully"));
    }

    @DeleteMapping("/{courseId}/topics/{topicId}")
    @Operation(summary = "Delete a topic from a course")
    public ResponseEntity<ApiResponse<Void>> deleteTopic(
            @PathVariable Long courseId,
            @PathVariable Long topicId) {
        courseService.deleteTopic(courseId, topicId);
        return ResponseEntity.ok(ApiResponse.success(null, "Topic deleted successfully"));
    }
}
