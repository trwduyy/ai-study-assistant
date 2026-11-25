package com.aistudy.assistant.admin.service.impl;

import com.aistudy.assistant.admin.dto.AuditLogResponse;
import com.aistudy.assistant.admin.entity.AuditLog;
import com.aistudy.assistant.admin.repository.AuditLogRepository;
import com.aistudy.assistant.admin.service.AuditLogService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Set<String> FORBIDDEN_METADATA_KEYS = Set.of(
            "password", "passwordhash", "token", "accesstoken", "refreshtoken",
            "secret", "authorization", "cookie", "credential", "apikey"
    );

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void record(
            Long actorUserId,
            String action,
            String objectType,
            Long objectId,
            Map<String, Object> metadata) {
        User actor = actorUserId != null ? userRepository.getReferenceById(actorUserId) : null;
        AuditLog entry = AuditLog.builder()
                .user(actor)
                .action(action)
                .objectType(objectType)
                .objectId(objectId)
                .metadataJson(sanitizeMetadata(metadata))
                .build();
        auditLogRepository.save(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLogs(Long userId, String action, Pageable pageable) {
        String normalizedAction = StringUtils.hasText(action) ? action.trim() : null;
        return auditLogRepository.findProjectedAuditLogs(userId, normalizedAction, pageable);
    }

    private Map<String, Object> sanitizeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            String lower = entry.getKey().toLowerCase(Locale.ROOT);
            boolean sensitive = FORBIDDEN_METADATA_KEYS.stream().anyMatch(lower::contains);
            if (!sensitive) {
                sanitized.put(entry.getKey(), entry.getValue());
            }
        }
        return sanitized.isEmpty() ? null : sanitized;
    }
}
