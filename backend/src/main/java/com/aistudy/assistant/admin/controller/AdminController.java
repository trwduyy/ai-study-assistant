package com.aistudy.assistant.admin.controller;

import com.aistudy.assistant.admin.dto.AdminClassroomResponse;
import com.aistudy.assistant.admin.dto.AuditLogResponse;
import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.dto.ResolveReportRequest;
import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.service.AdminService;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.dto.PageMeta;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrative governance endpoints protected by strict {@code ROLE_ADMIN} authorization.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin Governance V2", description = "Quản trị lớp học, báo cáo, cấu hình hệ thống và audit log")
@SecurityRequirement(name = "BearerAuth")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách toàn bộ lớp học kèm số lượng thành viên (Admin)")
    public ResponseEntity<ApiResponse<List<AdminClassroomResponse>>> listClasses(
            @RequestParam(value = "status", required = false) ClassroomStatus status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AdminClassroomResponse> page = adminService.listClasses(status, keyword, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/classes/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Khóa, mở khóa hoặc lưu trữ lớp học (Admin)")
    public ResponseEntity<ApiResponse<AdminClassroomResponse>> updateClassroomStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateClassroomStatusRequest request,
            @RequestHeader(value = "If-Match", required = false) String ifMatchHeader) {
        Integer ifMatchVersion = parseIfMatchVersion(ifMatchHeader);
        return ResponseEntity.ok(ApiResponse.success(
                adminService.updateClassroomStatus(id, request, ifMatchVersion)
        ));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Danh sách báo cáo từ người dùng (Admin)")
    public ResponseEntity<ApiResponse<List<ReportResponse>>> listReports(
            @RequestParam(value = "status", required = false) ReportStatus status,
            @RequestParam(value = "type", required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ReportResponse> page = adminService.listReports(status, type, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    @PutMapping("/reports/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật trạng thái xử lý báo cáo (Admin)")
    public ResponseEntity<ApiResponse<ReportResponse>> resolveReport(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ResolveReportRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminService.resolveReport(id, request)));
    }

    @GetMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Xem danh sách cấu hình hệ thống (Admin)")
    public ResponseEntity<ApiResponse<List<SystemSettingResponse>>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getSettings()));
    }

    @PutMapping("/settings")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cập nhật cấu hình hệ thống (Admin)")
    public ResponseEntity<ApiResponse<List<SystemSettingResponse>>> updateSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(adminService.updateSettings(request)));
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Tra cứu nhật ký audit hệ thống (Admin)")
    public ResponseEntity<ApiResponse<List<AuditLogResponse>>> listAuditLogs(
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "action", required = false) String action,
            @PageableDefault(size = 30, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AuditLogResponse> page = adminService.listAuditLogs(userId, action, pageable);
        return ResponseEntity.ok(ApiResponse.success(page.getContent(), PageMeta.ofPage(page)));
    }

    private Integer parseIfMatchVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            return null;
        }
        String normalized = ifMatchHeader.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.startsWith("\"") && normalized.endsWith("\"") && normalized.length() >= 2) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header If-Match phải là số nguyên version hợp lệ.");
        }
    }
}
