-- =============================================================================
-- AI Study Assistant V2 - Flyway Migration V6 (Phase 6: Admin, Reports, System Settings & Audit Logs)
-- Target DB : MySQL 8.0 (InnoDB, utf8mb4_unicode_ci)
-- Timezone  : All TIMESTAMP(3) columns store UTC timestamps
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. User Reports (UC-59)
-- -----------------------------------------------------------------------------
CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id BIGINT NOT NULL,
    type VARCHAR(64) NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id BIGINT NOT NULL,
    detail TEXT NOT NULL,
    status ENUM('OPEN', 'RESOLVED') NOT NULL DEFAULT 'OPEN',
    resolution_note TEXT NULL,
    handled_by BIGINT NULL,
    handled_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_reports_status_created (status, created_at),
    INDEX idx_reports_type_created (type, created_at),
    INDEX idx_reports_reporter (reporter_id),
    CONSTRAINT fk_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_reports_handled_by FOREIGN KEY (handled_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. System Settings (UC-60)
-- -----------------------------------------------------------------------------
CREATE TABLE system_settings (
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    description VARCHAR(255) NULL,
    updated_by BIGINT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (setting_key),
    CONSTRAINT fk_system_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO system_settings (setting_key, setting_value, description, updated_by)
VALUES
    ('AI_DAILY_QUOTA_PER_USER', '50', 'Số lượt gọi AI tối đa/ngày/người dùng (UTC)', NULL),
    ('GOOGLE_ALLOW_PERSONAL_ACCOUNTS', 'false', 'Chỉ cho phép tài khoản Học viện trừ khi Admin bật', NULL),
    ('MAX_UPLOAD_FILE_SIZE_MB', '20', 'Dung lượng tối đa (MB) cho mỗi tệp tải lên hệ thống', NULL);

-- -----------------------------------------------------------------------------
-- 3. Audit Logs
-- -----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    action VARCHAR(100) NOT NULL,
    object_type VARCHAR(64) NOT NULL,
    object_id BIGINT NULL,
    metadata_json JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_audit_user_created (user_id, created_at),
    INDEX idx_audit_object (object_type, object_id),
    INDEX idx_audit_action_created (action, created_at),
    CONSTRAINT fk_audit_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
