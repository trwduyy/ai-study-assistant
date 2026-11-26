package com.aistudy.assistant.admin.service.impl;

import com.aistudy.assistant.admin.service.AiQuotaService;
import com.aistudy.assistant.admin.service.SystemSettingService;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuotaServiceImpl implements AiQuotaService {

    private static final int DEFAULT_DAILY_QUOTA = 50;

    private final AiJobRepository aiJobRepository;
    private final AiChatMessageRepository aiChatMessageRepository;
    private final SystemSettingService systemSettingService;

    @Override
    @Transactional(readOnly = true)
    public void checkAndEnforceDailyQuota(Long userId) {
        int dailyQuota = systemSettingService.getIntSetting(
                SystemSettingService.KEY_AI_DAILY_QUOTA_PER_USER,
                DEFAULT_DAILY_QUOTA
        );

        Instant now = Instant.now();
        LocalDate todayUtc = LocalDate.ofInstant(now, ZoneOffset.UTC);
        Instant dayStart = todayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant nextDayStart = todayUtc.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);

        long jobsToday = aiJobRepository.countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                userId, dayStart, nextDayStart
        );
        long chatTurnsToday = aiChatMessageRepository
                .countByChatUserIdAndRoleAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                        userId, AiMessageRole.USER, dayStart, nextDayStart
                );

        long usedToday = jobsToday + chatTurnsToday;
        if (usedToday >= dailyQuota) {
            long retryAfterSeconds = Math.max(1L, Duration.between(now, nextDayStart).getSeconds());
            log.warn("AI daily quota exceeded for userId={}: used={} limit={} retryAfter={}s",
                    userId, usedToday, dailyQuota, retryAfterSeconds);
            throw new AppException(
                    ErrorCode.AI_QUOTA_EXCEEDED,
                    String.format(
                            "Bạn đã sử dụng hết hạn mức %d lượt AI trong ngày (%s UTC). Vui lòng thử lại sau %d giây.",
                            dailyQuota, todayUtc, retryAfterSeconds
                    ),
                    retryAfterSeconds
            );
        }
    }
}
