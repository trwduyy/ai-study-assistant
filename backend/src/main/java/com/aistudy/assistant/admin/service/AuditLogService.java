package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.dto.AuditLogResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Records security-sensitive and governance actions into {@code audit_logs} and serves Admin queries.
 */
public interface AuditLogService {

    void record(Long actorUserId, String action, String objectType, Long objectId, Map<String, Object> metadata);

    Page<AuditLogResponse> listAuditLogs(Long userId, String action, Pageable pageable);
}
