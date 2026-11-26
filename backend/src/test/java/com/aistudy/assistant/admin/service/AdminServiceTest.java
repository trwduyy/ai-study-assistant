package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.dto.ReportResponse;
import com.aistudy.assistant.admin.dto.ResolveReportRequest;
import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest.SettingUpdateItem;
import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.entity.SystemSetting;
import com.aistudy.assistant.admin.repository.ReportRepository;
import com.aistudy.assistant.admin.repository.SystemSettingRepository;
import com.aistudy.assistant.admin.service.impl.AdminServiceImpl;
import com.aistudy.assistant.admin.service.impl.SystemSettingServiceImpl;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassroomRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private ClassMemberRepository classMemberRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private SystemSettingRepository systemSettingRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditLogService auditLogService;

    private SystemSettingServiceImpl systemSettingService;
    private AdminServiceImpl adminService;

    private User adminUser;
    private User reporterUser;

    @BeforeEach
    void setUp() {
        systemSettingService = new SystemSettingServiceImpl(systemSettingRepository, userRepository, auditLogService);
        adminService = new AdminServiceImpl(
                classroomRepository,
                classMemberRepository,
                reportRepository,
                userRepository,
                systemSettingService,
                auditLogService
        );

        adminUser = User.builder()
                .id(1L)
                .email("admin@actvn.edu.vn")
                .fullName("Quản trị viên")
                .passwordHash("hash")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build();

        reporterUser = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Sinh Viên A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        UserPrincipal principal = UserPrincipal.create(adminUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("updateSettings updates GOOGLE_ALLOW_PERSONAL_ACCOUNTS and records audit log")
    void updateSettings_GoogleAllowPersonalAccounts_UpdatesAndAudits() {
        SystemSetting googleSetting = SystemSetting.builder()
                .settingKey(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS)
                .settingValue("false")
                .description("Allow personal accounts")
                .updatedAt(Instant.now())
                .build();

        when(userRepository.getReferenceById(1L)).thenReturn(adminUser);
        when(systemSettingRepository.findById(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS))
                .thenReturn(Optional.of(googleSetting));
        when(systemSettingRepository.save(any(SystemSetting.class))).thenAnswer(inv -> inv.getArgument(0));
        when(systemSettingRepository.findAllByOrderBySettingKeyAsc()).thenReturn(List.of(googleSetting));

        SettingUpdateItem item = new SettingUpdateItem();
        item.setSettingKey("google_allow_personal_accounts");
        item.setSettingValue("true");
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest();
        request.setSettings(List.of(item));

        List<SystemSettingResponse> responses = adminService.updateSettings(request);

        assertEquals(1, responses.size());
        assertEquals("true", responses.get(0).settingValue());
        assertTrue(systemSettingService.getBooleanSetting(
                SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS, false
        ));

        ArgumentCaptor<Map<String, Object>> metadataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(auditLogService).record(
                eq(1L),
                eq("SYSTEM_SETTINGS_UPDATED"),
                eq("SYSTEM_SETTING"),
                isNull(),
                metadataCaptor.capture()
        );
        assertEquals(List.of(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS),
                metadataCaptor.getValue().get("updatedKeys"));
    }

    @Test
    @DisplayName("updateSettings rejects invalid boolean for GOOGLE_ALLOW_PERSONAL_ACCOUNTS")
    void updateSettings_InvalidBoolean_ThrowsValidationError() {
        SystemSetting googleSetting = SystemSetting.builder()
                .settingKey(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS)
                .settingValue("false")
                .updatedAt(Instant.now())
                .build();

        when(userRepository.getReferenceById(1L)).thenReturn(adminUser);
        when(systemSettingRepository.findById(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS))
                .thenReturn(Optional.of(googleSetting));

        SettingUpdateItem item = new SettingUpdateItem();
        item.setSettingKey(SystemSettingService.KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS);
        item.setSettingValue("not-a-bool");
        UpdateSystemSettingsRequest request = new UpdateSystemSettingsRequest();
        request.setSettings(List.of(item));

        AppException ex = assertThrows(AppException.class, () -> adminService.updateSettings(request));
        assertEquals(ErrorCode.VALIDATION_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("resolveReport marks report RESOLVED with handler metadata and writes audit log")
    void resolveReport_MarksResolvedAndWritesAuditLog() {
        Report report = Report.builder()
                .id(301L)
                .reporter(reporterUser)
                .type("AI_INACCURATE")
                .targetType("AI_CHAT_MESSAGE")
                .targetId(812L)
                .detail("Trích dẫn sai số trang.")
                .status(ReportStatus.OPEN)
                .build();

        when(reportRepository.findWithDetailsById(301L)).thenReturn(Optional.of(report));
        when(userRepository.findById(1L)).thenReturn(Optional.of(adminUser));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ResolveReportRequest request = new ResolveReportRequest();
        request.setStatus(ReportStatus.RESOLVED);
        request.setResolutionNote("Đã cập nhật prompt grounding.");

        ReportResponse response = adminService.resolveReport(301L, request);

        assertEquals(ReportStatus.RESOLVED, response.status());
        assertEquals("Đã cập nhật prompt grounding.", response.resolutionNote());
        assertEquals(1L, response.handledById());
        assertNotNull(response.handledAt());

        verify(auditLogService).record(
                eq(1L),
                eq("REPORT_RESOLVED"),
                eq("REPORT"),
                eq(301L),
                any()
        );
    }
}
