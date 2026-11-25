package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.dto.AdminClassroomResponse;
import com.aistudy.assistant.admin.dto.AuditLogResponse;
import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.dto.ResolveReportRequest;
import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Administrative governance service covering classroom moderation, report resolution,
 * dynamic system settings, and audit log inspection (UC-59, UC-60).
 */
public interface AdminService {

    Page<AdminClassroomResponse> listClasses(ClassroomStatus status, String keyword, Pageable pageable);

    AdminClassroomResponse updateClassroomStatus(
            Long classId,
            UpdateClassroomStatusRequest request,
            Integer ifMatchVersion
    );

    Page<ReportResponse> listReports(ReportStatus status, String type, Pageable pageable);

    ReportResponse resolveReport(Long reportId, ResolveReportRequest request);

    List<SystemSettingResponse> getSettings();

    List<SystemSettingResponse> updateSettings(UpdateSystemSettingsRequest request);

    Page<AuditLogResponse> listAuditLogs(Long userId, String action, Pageable pageable);
}
