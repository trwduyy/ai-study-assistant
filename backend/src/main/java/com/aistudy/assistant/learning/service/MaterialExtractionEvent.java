package com.aistudy.assistant.learning.service;

/**
 * Domain event published inside a DB transaction to trigger asynchronous extraction after commit.
 */
public record MaterialExtractionEvent(Long materialId) {
}
