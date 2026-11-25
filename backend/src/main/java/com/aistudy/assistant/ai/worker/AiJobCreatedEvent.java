package com.aistudy.assistant.ai.worker;

/**
 * Domain event published inside a transaction when a new {@code ai_jobs} task is queued.
 */
public record AiJobCreatedEvent(String jobId) {
}
