package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;

import java.util.List;

/**
 * Manages runtime {@code system_settings} with lightweight caching and validation.
 */
public interface SystemSettingService {

    String KEY_AI_DAILY_QUOTA_PER_USER = "AI_DAILY_QUOTA_PER_USER";
    String KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS = "GOOGLE_ALLOW_PERSONAL_ACCOUNTS";
    String KEY_MAX_UPLOAD_FILE_SIZE_MB = "MAX_UPLOAD_FILE_SIZE_MB";

    int getIntSetting(String key, int defaultValue);

    boolean getBooleanSetting(String key, boolean defaultValue);

    List<SystemSettingResponse> getAllSettings();

    List<SystemSettingResponse> updateSettings(UpdateSystemSettingsRequest request);
}
