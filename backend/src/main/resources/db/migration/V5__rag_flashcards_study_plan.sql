-- =============================================================================
-- AI Study Assistant V2 - Flyway Migration V5 (Phase 5: RAG Embeddings, Flashcards, Goals, Tasks, Study Plan)
-- Target DB : MySQL 8.0 (InnoDB, utf8mb4_unicode_ci)
-- Timezone  : All TIMESTAMP(3) columns store UTC timestamps
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. RAG Chunk Embeddings (FR-04, FR-05)
-- -----------------------------------------------------------------------------
CREATE TABLE material_chunk_embeddings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    chunk_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    model VARCHAR(64) NOT NULL,
    dimensions INT NOT NULL,
    embedding_json LONGTEXT NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_mce_chunk (chunk_id),
    INDEX idx_mce_material (material_id),
    CONSTRAINT fk_mce_chunk FOREIGN KEY (chunk_id) REFERENCES material_chunks (id) ON DELETE CASCADE,
    CONSTRAINT fk_mce_material FOREIGN KEY (material_id) REFERENCES materials (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Flashcards & Spaced Repetition SM-2 (FR-08)
-- -----------------------------------------------------------------------------
CREATE TABLE flashcard_decks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    course_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_flashcard_decks_owner_created (owner_id, created_at),
    INDEX idx_flashcard_decks_course (course_id),
    CONSTRAINT fk_flashcard_decks_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_flashcard_decks_course FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE flashcards (
    id BIGINT NOT NULL AUTO_INCREMENT,
    deck_id BIGINT NOT NULL,
    front TEXT NOT NULL,
    back TEXT NOT NULL,
    source_chunk_id BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_flashcards_deck_id (deck_id, id),
    INDEX idx_flashcards_source_chunk (source_chunk_id),
    CONSTRAINT fk_flashcards_deck FOREIGN KEY (deck_id) REFERENCES flashcard_decks (id) ON DELETE CASCADE,
    CONSTRAINT fk_flashcards_source_chunk FOREIGN KEY (source_chunk_id) REFERENCES material_chunks (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE flashcard_reviews (
    id BIGINT NOT NULL AUTO_INCREMENT,
    card_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    rating ENUM('FORGOT', 'HARD', 'OK', 'EASY') NOT NULL,
    ease_factor DOUBLE NOT NULL DEFAULT 2.5,
    interval_days INT NOT NULL DEFAULT 0,
    repetitions INT NOT NULL DEFAULT 0,
    next_review_at TIMESTAMP(3) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_fr_user_next (user_id, next_review_at),
    INDEX idx_fr_card_user_id (card_id, user_id, id),
    CONSTRAINT fk_fr_card FOREIGN KEY (card_id) REFERENCES flashcards (id) ON DELETE CASCADE,
    CONSTRAINT fk_fr_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Study Goals, Daily Tasks & AI Study Plans (FR-17)
-- -----------------------------------------------------------------------------
CREATE TABLE goals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    subject VARCHAR(128) NULL,
    due_date DATE NULL,
    priority ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'MEDIUM',
    status ENUM('ACTIVE', 'COMPLETED', 'CANCELLED') NOT NULL DEFAULT 'ACTIVE',
    free_time JSON NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_goals_user_status (user_id, status, due_date),
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tasks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    goal_id BIGINT NULL,
    title VARCHAR(255) NOT NULL,
    task_date DATE NOT NULL,
    priority ENUM('LOW', 'MEDIUM', 'HIGH') NOT NULL DEFAULT 'MEDIUM',
    status ENUM('TODO', 'DONE', 'MISSED') NOT NULL DEFAULT 'TODO',
    remind_at TIMESTAMP(3) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_tasks_user_date (user_id, task_date),
    INDEX idx_tasks_goal (goal_id),
    CONSTRAINT fk_tasks_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_tasks_goal FOREIGN KEY (goal_id) REFERENCES goals (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE study_plans (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    job_id CHAR(36) NOT NULL,
    status ENUM('PROPOSED', 'PARTIAL', 'CLOSED') NOT NULL DEFAULT 'PROPOSED',
    rationale TEXT NULL,
    model VARCHAR(64) NULL,
    prompt_version VARCHAR(32) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    INDEX idx_study_plans_user_created (user_id, created_at),
    INDEX idx_study_plans_job (job_id),
    CONSTRAINT fk_study_plans_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_study_plans_job FOREIGN KEY (job_id) REFERENCES ai_jobs (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE study_plan_items (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    planned_start TIMESTAMP(3) NOT NULL,
    planned_end TIMESTAMP(3) NOT NULL,
    decision ENUM('PENDING', 'ACCEPTED', 'EDITED', 'REJECTED') NOT NULL DEFAULT 'PENDING',
    task_id BIGINT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_spi_task (task_id),
    INDEX idx_spi_plan_id (plan_id, id),
    CONSTRAINT fk_spi_plan FOREIGN KEY (plan_id) REFERENCES study_plans (id) ON DELETE CASCADE,
    CONSTRAINT fk_spi_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
