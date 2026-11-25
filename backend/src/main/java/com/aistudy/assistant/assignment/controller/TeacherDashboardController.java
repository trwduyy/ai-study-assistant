package com.aistudy.assistant.assignment.controller;

import com.aistudy.assistant.assignment.dto.TeacherDashboardResponse;
import com.aistudy.assistant.assignment.service.TeacherDashboardService;
import com.aistudy.assistant.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for Teacher Dashboard summary (FR-20).
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Teacher Dashboard V2", description = "Bảng điều khiển tổng hợp giảng viên")
@SecurityRequirement(name = "BearerAuth")
public class TeacherDashboardController {

    private final TeacherDashboardService teacherDashboardService;

    @GetMapping("/dashboard/teacher")
    @PreAuthorize("hasAnyRole('TEACHER', 'ADMIN')")
    @Operation(summary = "Dashboard tổng hợp cho giảng viên")
    public ResponseEntity<ApiResponse<TeacherDashboardResponse>> getTeacherDashboard() {
        return ResponseEntity.ok(ApiResponse.success(teacherDashboardService.getTeacherDashboard()));
    }
}
