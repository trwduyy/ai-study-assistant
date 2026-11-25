-- =============================================================================
-- AI Study Assistant V2 - Flyway Migration V4 (Phase 4: Calendar & Meeting)
-- Target DB : MySQL 8.0 (InnoDB, utf8mb4_unicode_ci)
-- Timezone  : All TIMESTAMP(3) columns store UTC timestamps
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Calendar Imports (FR-15)
-- -----------------------------------------------------------------------------
CREATE TABLE calendar_imports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    source_type ENUM('ICS', 'CSV') NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status ENUM('PREVIEW', 'CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'PREVIEW',
    preview_json LONGTEXT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_calendar_imports_user_created (user_id, created_at),
    CONSTRAINT fk_calendar_imports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Events (FR-15, FR-16)
-- -----------------------------------------------------------------------------
CREATE TABLE events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    class_id BIGINT NULL,
    import_id BIGINT NULL,
    type ENUM('CLASS', 'TEST', 'MEETING', 'PERSONAL') NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT NULL,
    start_at TIMESTAMP(3) NOT NULL,
    end_at TIMESTAMP(3) NOT NULL,
    timezone VARCHAR(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    status ENUM('UPCOMING', 'ONGOING', 'ENDED', 'CANCELLED') NOT NULL DEFAULT 'UPCOMING',
    meeting_url VARCHAR(500) NULL,
    source ENUM('MANUAL', 'ICS', 'CSV', 'GOOGLE') NOT NULL DEFAULT 'MANUAL',
    external_id VARCHAR(255) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_events_owner_start (owner_id, start_at),
    INDEX idx_events_class_start (class_id, start_at),
    INDEX idx_events_type_status_start (type, status, start_at),
    UNIQUE KEY uk_events_owner_source_ext (owner_id, source, external_id),
    CONSTRAINT fk_events_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_events_import FOREIGN KEY (import_id) REFERENCES calendar_imports (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. External Accounts (Google Calendar Read-Only OAuth Integration - FR-16)
-- -----------------------------------------------------------------------------
CREATE TABLE external_accounts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    scopes VARCHAR(500) NULL,
    refresh_token_encrypted TEXT NOT NULL,
    connected_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_synced_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_accounts_user_provider (user_id, provider),
    CONSTRAINT fk_external_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
