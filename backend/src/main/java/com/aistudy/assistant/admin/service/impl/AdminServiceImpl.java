package com.aistudy.assistant.admin.service.impl;

import com.aistudy.assistant.admin.dto.AdminClassroomResponse;
import com.aistudy.assistant.admin.dto.AuditLogResponse;
import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.dto.ResolveReportRequest;
import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;
import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.repository.ReportRepository;
import com.aistudy.assistant.admin.service.AdminService;
import com.aistudy.assistant.admin.service.AuditLogService;
import com.aistudy.assistant.admin.service.SystemSettingService;
import com.aistudy.assistant.classroom.dto.UpdateClassroomStatusRequest;
import com.aistudy.assistant.classroom.entity.ClassMemberStatus;
import com.aistudy.assistant.classroom.entity.Classroom;
import com.aistudy.assistant.classroom.entity.ClassroomStatus;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final ClassroomRepository classroomRepository;
    private final ClassMemberRepository classMemberRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final SystemSettingService systemSettingService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminClassroomResponse> listClasses(ClassroomStatus status, String keyword, Pageable pageable) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        return classroomRepository.findProjectedAllForAdmin(status, normalizedKeyword, pageable);
    }

    @Override
    @Transactional
    public AdminClassroomResponse updateClassroomStatus(
            Long classId,
            UpdateClassroomStatusRequest request,
            Integer ifMatchVersion) {
        Long adminUserId = SecurityUtils.currentUserId();
        Classroom classroom = classroomRepository.findWithOwnerById(classId)
                .orElseThrow(() -> new AppException(ErrorCode.CLASS_NOT_FOUND));

        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : request.getVersion();
        if (expectedVersion != null && !Objects.equals(classroom.getVersion(), expectedVersion)) {
            throw new AppException(ErrorCode.VERSION_CONFLICT);
        }

        ClassroomStatus previousStatus = classroom.getStatus();
        classroom.setStatus(request.getStatus());
        Classroom updated = classroomRepository.saveAndFlush(classroom);

        long activeMemberCount = classMemberRepository.countByClassroomIdAndStatus(
                classId, ClassMemberStatus.ACTIVE
        );

        auditLogService.record(
                adminUserId,
                "ADMIN_CLASSROOM_STATUS_UPDATED",
                "CLASSROOM",
                classId,
                Map.of(
                        "classCode", updated.getCode(),
                        "oldStatus", previousStatus.name(),
                        "newStatus", updated.getStatus().name()
                )
        );
        log.info("Admin userId={} updated classroom id={} status from {} to {}",
                adminUserId, classId, previousStatus, updated.getStatus());

        User owner = updated.getOwner();
        return new AdminClassroomResponse(
                updated.getId(),
                updated.getCode(),
                updated.getName(),
                updated.getDescription(),
                updated.getSubject(),
                updated.getStatus(),
                owner != null ? owner.getId() : null,
                owner != null ? owner.getFullName() : null,
                owner != null ? owner.getEmail() : null,
                activeMemberCount,
                updated.getVersion(),
                updated.getCreatedAt(),
                updated.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReportResponse> listReports(ReportStatus status, String type, Pageable pageable) {
        String normalizedType = StringUtils.hasText(type) ? type.trim() : null;
        return reportRepository.findProjectedReports(status, normalizedType, pageable);
    }

    @Override
    @Transactional
    public ReportResponse resolveReport(Long reportId, ResolveReportRequest request) {
        Long adminUserId = SecurityUtils.currentUserId();
        Report report = reportRepository.findWithDetailsById(reportId)
                .orElseThrow(() -> new AppException(ErrorCode.REPORT_NOT_FOUND));

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        ReportStatus targetStatus = request != null && request.getStatus() != null
                ? request.getStatus()
                : ReportStatus.RESOLVED;
        String resolutionNote = request != null && StringUtils.hasText(request.getResolutionNote())
                ? request.getResolutionNote().trim()
                : null;

        report.setStatus(targetStatus);
        report.setResolutionNote(resolutionNote);
        report.setHandledBy(adminUser);
        report.setHandledAt(Instant.now());

        Report saved = reportRepository.save(report);

        auditLogService.record(
                adminUserId,
                "REPORT_RESOLVED",
                "REPORT",
                saved.getId(),
                Map.of(
                        "reportType", saved.getType(),
                        "targetType", saved.getTargetType(),
                        "targetId", saved.getTargetId(),
                        "status", saved.getStatus().name()
                )
        );
        log.info("Admin userId={} resolved report id={} with status={}", adminUserId, saved.getId(), saved.getStatus());

        User reporter = saved.getReporter();
        return new ReportResponse(
                saved.getId(),
                reporter != null ? reporter.getId() : null,
                reporter != null ? reporter.getFullName() : null,
                reporter != null ? reporter.getEmail() : null,
                saved.getType(),
                saved.getTargetType(),
                saved.getTargetId(),
                saved.getDetail(),
                saved.getStatus(),
                saved.getResolutionNote(),
                adminUser.getId(),
                adminUser.getFullName(),
                saved.getHandledAt(),
                saved.getCreatedAt(),
                saved.getUpdatedAt()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getSettings() {
        return systemSettingService.getAllSettings();
    }

    @Override
    @Transactional
    public List<SystemSettingResponse> updateSettings(UpdateSystemSettingsRequest request) {
        return systemSettingService.updateSettings(request);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLogs(Long userId, String action, Pageable pageable) {
        return auditLogService.listAuditLogs(userId, action, pageable);
    }
}
