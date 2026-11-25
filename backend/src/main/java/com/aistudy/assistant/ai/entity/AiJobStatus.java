package com.aistudy.assistant.ai.entity;

/**
 * Execution lifecycle states for asynchronous AI jobs in {@code ai_jobs.status}.
 */
public enum AiJobStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
