package com.aistudy.assistant.ai.worker;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Dispatches newly queued AI jobs to the async worker only after the queuing transaction commits.
 */
@Component
@RequiredArgsConstructor
public class AiJobListener {

    private final AiJobWorker aiJobWorker;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiJobCreated(AiJobCreatedEvent event) {
        aiJobWorker.processJobAsync(event.jobId());
    }
}
