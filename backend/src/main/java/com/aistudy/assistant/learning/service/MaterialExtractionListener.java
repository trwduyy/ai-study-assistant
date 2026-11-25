package com.aistudy.assistant.learning.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts asynchronous file extraction only after the material metadata transaction commits.
 */
@Component
@RequiredArgsConstructor
public class MaterialExtractionListener {

    private final FileProcessingService fileProcessingService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMaterialExtractionRequested(MaterialExtractionEvent event) {
        fileProcessingService.extractAsync(event.materialId());
    }
}
