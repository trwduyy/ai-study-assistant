-- =============================================================================
-- AI Study Assistant V2 - Flyway Migration V2 (Phase 2: Classroom & Assignment)
-- Target DB : MySQL 8.0 (InnoDB, utf8mb4_unicode_ci)
-- Timezone  : All TIMESTAMP(3) columns store UTC timestamps
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Classroom Module (FR-12)
-- -----------------------------------------------------------------------------
CREATE TABLE classrooms (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT NULL,
    subject VARCHAR(150) NULL,
    avatar_url VARCHAR(500) NULL,
    start_at TIMESTAMP(3) NULL,
    end_at TIMESTAMP(3) NULL,
    status ENUM('OPEN', 'CLOSED', 'ARCHIVED') NOT NULL DEFAULT 'OPEN',
    owner_id BIGINT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_classrooms_code (code),
    INDEX idx_classrooms_owner_status (owner_id, status),
    INDEX idx_classrooms_status_created (status, created_at),
    CONSTRAINT fk_classrooms_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_members (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    class_role ENUM('OWNER_TEACHER', 'CO_TEACHER', 'STUDENT') NOT NULL,
    status ENUM('ACTIVE', 'LEFT', 'REMOVED') NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_members_class_user (class_id, user_id),
    INDEX idx_class_members_user_status (user_id, status),
    INDEX idx_class_members_class_role_status (class_id, class_role, status),
    CONSTRAINT fk_class_members_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_join_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    status ENUM('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED') NOT NULL DEFAULT 'PENDING',
    pending_flag TINYINT GENERATED ALWAYS AS (IF(status = 'PENDING', 1, NULL)) STORED,
    decided_by BIGINT NULL,
    decided_at TIMESTAMP(3) NULL,
    rejection_reason VARCHAR(500) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_class_join_requests_pending (class_id, student_id, pending_flag),
    INDEX idx_class_join_requests_class_status (class_id, status, created_at),
    INDEX idx_class_join_requests_student_status (student_id, status, created_at),
    CONSTRAINT fk_class_join_requests_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_join_requests_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_join_requests_decider FOREIGN KEY (decided_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_posts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    content LONGTEXT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_class_posts_class_pinned_created (class_id, pinned, created_at),
    CONSTRAINT fk_class_posts_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_posts_author FOREIGN KEY (author_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_materials (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    uploader_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    mime_type VARCHAR(120) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_class_materials_class_created (class_id, created_at),
    CONSTRAINT fk_class_materials_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_materials_uploader FOREIGN KEY (uploader_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Assignment & Class Test Module (FR-13, FR-14)
-- -----------------------------------------------------------------------------
CREATE TABLE assignments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    creator_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    instructions TEXT NULL,
    max_score DECIMAL(6, 2) NOT NULL DEFAULT 10.00,
    open_at TIMESTAMP(3) NULL,
    due_at TIMESTAMP(3) NULL,
    allow_late BOOLEAN NOT NULL DEFAULT FALSE,
    max_submissions INT NOT NULL DEFAULT 1,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_assignments_class_due (class_id, due_at),
    INDEX idx_assignments_creator (creator_id),
    CONSTRAINT fk_assignments_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_assignments_creator FOREIGN KEY (creator_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE assignment_attachments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_assignment_attachments_assignment (assignment_id),
    CONSTRAINT fk_assignment_attachments_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE submissions (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    text_answer LONGTEXT NULL,
    status ENUM('SUBMITTED', 'GRADED', 'RETURNED') NOT NULL DEFAULT 'SUBMITTED',
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    submitted_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    score DECIMAL(6, 2) NULL,
    feedback TEXT NULL,
    graded_by BIGINT NULL,
    graded_at TIMESTAMP(3) NULL,
    idempotency_key VARCHAR(64) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_submissions_assignment_student (assignment_id, student_id),
    UNIQUE KEY uk_submissions_idempotency_key (idempotency_key),
    INDEX idx_submissions_assignment_status (assignment_id, status),
    INDEX idx_submissions_student_submitted (student_id, submitted_at),
    CONSTRAINT fk_submissions_assignment FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_submissions_grader FOREIGN KEY (graded_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE submission_files (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    stored_path VARCHAR(500) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_submission_files_submission (submission_id),
    CONSTRAINT fk_submission_files_submission FOREIGN KEY (submission_id) REFERENCES submissions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_tests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    class_id BIGINT NOT NULL,
    quiz_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    start_at TIMESTAMP(3) NOT NULL,
    end_at TIMESTAMP(3) NOT NULL,
    duration_minutes INT NOT NULL,
    max_attempts INT NOT NULL DEFAULT 1,
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_class_tests_class_window (class_id, start_at, end_at),
    INDEX idx_class_tests_quiz (quiz_id),
    CONSTRAINT fk_class_tests_class FOREIGN KEY (class_id) REFERENCES classrooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_class_tests_quiz FOREIGN KEY (quiz_id) REFERENCES quizzes (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE quiz_attempts
    ADD CONSTRAINT fk_quiz_attempts_test FOREIGN KEY (test_id) REFERENCES class_tests(id) ON DELETE SET NULL;
