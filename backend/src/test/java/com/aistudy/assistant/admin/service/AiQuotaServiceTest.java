package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.service.impl.AiQuotaServiceImpl;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.common.dto.ApiResponse;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiQuotaServiceTest {

    @Mock
    private AiJobRepository aiJobRepository;

    @Mock
    private AiChatMessageRepository aiChatMessageRepository;

    @Mock
    private SystemSettingService systemSettingService;

    @InjectMocks
    private AiQuotaServiceImpl aiQuotaService;

    @Test
    @DisplayName("checkAndEnforceDailyQuota allows request when combined daily usage is below limit")
    void checkAndEnforceDailyQuota_BelowLimit_Succeeds() {
        when(systemSettingService.getIntSetting(SystemSettingService.KEY_AI_DAILY_QUOTA_PER_USER, 50))
                .thenReturn(50);
        when(aiJobRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(11L), any(Instant.class), any(Instant.class))).thenReturn(20L);
        when(aiChatMessageRepository.countByChatUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(11L), eq(AiMessageRole.USER), any(Instant.class), any(Instant.class))).thenReturn(29L);

        assertDoesNotThrow(() -> aiQuotaService.checkAndEnforceDailyQuota(11L));
    }

    @Test
    @DisplayName("checkAndEnforceDailyQuota throws 429 AI_QUOTA_EXCEEDED with Retry-After when daily quota reached")
    void checkAndEnforceDailyQuota_ExceedsLimit_Throws429WithRetryAfter() {
        when(systemSettingService.getIntSetting(SystemSettingService.KEY_AI_DAILY_QUOTA_PER_USER, 50))
                .thenReturn(10);
        when(aiJobRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(11L), any(Instant.class), any(Instant.class))).thenReturn(6L);
        when(aiChatMessageRepository.countByChatUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                eq(11L), eq(AiMessageRole.USER), any(Instant.class), any(Instant.class))).thenReturn(4L);

        AppException ex = assertThrows(AppException.class, () -> aiQuotaService.checkAndEnforceDailyQuota(11L));
        assertEquals(ErrorCode.AI_QUOTA_EXCEEDED, ex.getErrorCode());
        assertNotNull(ex.getRetryAfterSeconds());
        assertTrue(ex.getRetryAfterSeconds() >= 1L && ex.getRetryAfterSeconds() <= 86400L);

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ApiResponse<Void>> response = handler.handleAppException(ex, new MockHttpServletRequest());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, response.getStatusCode());
        assertEquals(String.valueOf(ex.getRetryAfterSeconds()), response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals("AI_QUOTA_EXCEEDED", response.getBody().getError().getCode());
    }
}
