package com.aistudy.assistant.admin.service.impl;

import com.aistudy.assistant.admin.dto.SystemSettingResponse;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest;
import com.aistudy.assistant.admin.dto.UpdateSystemSettingsRequest.SettingUpdateItem;
import com.aistudy.assistant.admin.entity.SystemSetting;
import com.aistudy.assistant.admin.repository.SystemSettingRepository;
import com.aistudy.assistant.admin.service.AuditLogService;
import com.aistudy.assistant.admin.service.SystemSettingService;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final int MAX_QUOTA_LIMIT = 100_000;
    private static final int MAX_UPLOAD_MB_LIMIT = 500;

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    private final Map<String, CachedSetting> cache = new ConcurrentHashMap<>();

    @Override
    @Transactional(readOnly = true)
    public int getIntSetting(String key, int defaultValue) {
        String raw = getRawSetting(key);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean getBooleanSetting(String key, boolean defaultValue) {
        String raw = getRawSetting(key);
        if (!StringUtils.hasText(raw)) {
            return defaultValue;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        return defaultValue;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SystemSettingResponse> getAllSettings() {
        return systemSettingRepository.findAllByOrderBySettingKeyAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<SystemSettingResponse> updateSettings(UpdateSystemSettingsRequest request) {
        Long adminUserId = SecurityUtils.currentUserId();
        User adminUser = userRepository.getReferenceById(adminUserId);
        Instant now = Instant.now();

        List<String> updatedKeys = new ArrayList<>();
        Map<String, Map<String, String>> changeDetails = new LinkedHashMap<>();

        for (SettingUpdateItem item : request.getSettings()) {
            String key = item.getSettingKey().trim().toUpperCase(Locale.ROOT);
            SystemSetting setting = systemSettingRepository.findById(key)
                    .orElseThrow(() -> new AppException(
                            ErrorCode.SETTING_NOT_FOUND,
                            "Không tìm thấy cấu hình hệ thống '" + key + "'."
                    ));

            String normalizedValue = validateAndNormalizeValue(key, item.getSettingValue());
            String oldValue = setting.getSettingValue();
            setting.setSettingValue(normalizedValue);
            setting.setUpdatedBy(adminUser);
            setting.setUpdatedAt(now);
            systemSettingRepository.save(setting);

            cache.remove(key);
            updatedKeys.add(key);
            changeDetails.put(key, Map.of("oldValue", oldValue, "newValue", normalizedValue));
        }

        auditLogService.record(
                adminUserId,
                "SYSTEM_SETTINGS_UPDATED",
                "SYSTEM_SETTING",
                null,
                Map.of("updatedKeys", updatedKeys, "changes", changeDetails)
        );
        log.info("Admin userId={} updated system settings keys={}", adminUserId, updatedKeys);

        return getAllSettings();
    }

    private String getRawSetting(String key) {
        Instant now = Instant.now();
        CachedSetting cached = cache.get(key);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        String dbValue = systemSettingRepository.findById(key)
                .map(SystemSetting::getSettingValue)
                .orElse(null);
        if (dbValue != null) {
            cache.put(key, new CachedSetting(dbValue, now.plus(CACHE_TTL)));
        }
        return dbValue;
    }

    private String validateAndNormalizeValue(String key, String rawValue) {
        String trimmed = rawValue != null ? rawValue.trim() : "";
        switch (key) {
            case KEY_AI_DAILY_QUOTA_PER_USER -> {
                int parsed = parsePositiveInt(key, trimmed, MAX_QUOTA_LIMIT);
                return String.valueOf(parsed);
            }
            case KEY_MAX_UPLOAD_FILE_SIZE_MB -> {
                int parsed = parsePositiveInt(key, trimmed, MAX_UPLOAD_MB_LIMIT);
                return String.valueOf(parsed);
            }
            case KEY_GOOGLE_ALLOW_PERSONAL_ACCOUNTS -> {
                String lower = trimmed.toLowerCase(Locale.ROOT);
                if (!"true".equals(lower) && !"false".equals(lower)) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            "Giá trị của " + key + " phải là true hoặc false.",
                            List.of(new FieldErrorDetail(key, "Chỉ chấp nhận giá trị true hoặc false."))
                    );
                }
                return lower;
            }
            default -> {
                if (!StringUtils.hasText(trimmed)) {
                    throw new AppException(
                            ErrorCode.VALIDATION_ERROR,
                            "Giá trị cấu hình " + key + " không được để trống.",
                            List.of(new FieldErrorDetail(key, "Giá trị không được để trống."))
                    );
                }
                return trimmed;
            }
        }
    }

    private int parsePositiveInt(String key, String value, int maxAllowed) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > maxAllowed) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "Giá trị của " + key + " phải là số nguyên dương trong khoảng [1, " + maxAllowed + "].",
                        List.of(new FieldErrorDetail(key, "Giá trị phải nằm trong khoảng 1 đến " + maxAllowed + "."))
                );
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Giá trị của " + key + " phải là số nguyên dương hợp lệ.",
                    List.of(new FieldErrorDetail(key, "Phải là số nguyên dương."))
            );
        }
    }

    private SystemSettingResponse toResponse(SystemSetting setting) {
        User updater = setting.getUpdatedBy();
        return new SystemSettingResponse(
                setting.getSettingKey(),
                setting.getSettingValue(),
                setting.getDescription(),
                updater != null ? updater.getId() : null,
                updater != null ? updater.getFullName() : null,
                setting.getUpdatedAt()
        );
    }

    private record CachedSetting(String value, Instant expiresAt) {
    }
}
