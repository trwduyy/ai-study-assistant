package com.aistudy.assistant.admin.service;

/**
 * Enforces per-user daily AI request quotas against {@code AI_DAILY_QUOTA_PER_USER}.
 */
public interface AiQuotaService {

    void checkAndEnforceDailyQuota(Long userId);
}
