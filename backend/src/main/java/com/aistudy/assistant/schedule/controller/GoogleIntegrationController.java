package com.aistudy.assistant.schedule.controller;

import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.schedule.dto.GoogleAuthorizeResponse;
import com.aistudy.assistant.schedule.dto.GoogleConnectionResponse;
import com.aistudy.assistant.schedule.dto.GoogleSyncRequest;
import com.aistudy.assistant.schedule.dto.GoogleSyncResponse;
import com.aistudy.assistant.schedule.service.GoogleIntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for read-only Google Calendar OAuth and synchronization (FR-16, D-06).
 */
@RestController
@RequestMapping("/api/v1/integrations/google")
@RequiredArgsConstructor
@Tag(name = "Google Calendar Integration V2", description = "Kết nối OAuth và đồng bộ đọc lịch Google Calendar (chỉ đọc)")
@SecurityRequirement(name = "BearerAuth")
public class GoogleIntegrationController {

    private final GoogleIntegrationService googleIntegrationService;

    @GetMapping("/authorize")
    @Operation(summary = "Khởi tạo liên kết OAuth Google Calendar (scope chỉ đọc)")
    public ResponseEntity<ApiResponse<GoogleAuthorizeResponse>> authorize() {
        return ResponseEntity.ok(ApiResponse.success(googleIntegrationService.authorize()));
    }

    @GetMapping("/callback")
    @Operation(summary = "Callback nhận OAuth code và lưu refresh token mã hóa AES-256-GCM")
    public ResponseEntity<ApiResponse<GoogleConnectionResponse>> callback(
            @RequestParam("code") String code,
            @RequestParam("state") String state) {
        return ResponseEntity.ok(ApiResponse.success(googleIntegrationService.handleCallback(code, state)));
    }

    @PostMapping("/sync")
    @Operation(summary = "Đồng bộ đọc sự kiện từ Google Calendar (không ghi ngược)")
    public ResponseEntity<ApiResponse<GoogleSyncResponse>> sync(
            @RequestBody(required = false) GoogleSyncRequest request) {
        return ResponseEntity.ok(ApiResponse.success(googleIntegrationService.syncCalendar(request)));
    }

    @DeleteMapping
    @Operation(summary = "Ngắt kết nối Google Calendar và xóa token đã lưu")
    public ResponseEntity<ApiResponse<Void>> disconnect() {
        googleIntegrationService.disconnectGoogle();
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
