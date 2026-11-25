package com.aistudy.assistant.ai.worker;

import com.aistudy.assistant.ai.entity.AiJobType;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.repository.AiJobRepository;
import com.aistudy.assistant.ai.repository.MaterialChunkEmbeddingRepository;
import com.aistudy.assistant.ai.service.AiFlashcardValidator;
import com.aistudy.assistant.ai.service.AiQuizValidator;
import com.aistudy.assistant.ai.service.ChunkGroundingService;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.ai.service.HybridChunkRetrieverServiceImpl;
import com.aistudy.assistant.ai.worker.AiJobTxService.ClaimedJobSnapshot;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiJobWorkerTest {

    @Mock
    private AiJobRepository aiJobRepository;
    @Mock
    private AiJobTxService aiJobTxService;
    @Mock
    private GeminiClientService geminiClientService;
    @Mock
    private MaterialChunkEmbeddingRepository embeddingRepository;
    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private AiJobWorker worker;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        worker = new AiJobWorker(
                aiJobRepository,
                aiJobTxService,
                geminiClientService,
                new HybridChunkRetrieverServiceImpl(
                        embeddingRepository,
                        geminiClientService,
                        new ChunkGroundingService(),
                        objectMapper,
                        0.45
                ),
                new AiQuizValidator(objectMapper),
                new AiFlashcardValidator(objectMapper),
                objectMapper,
                messagingTemplate
        );
    }

    @Test
    @DisplayName("processJob retries Gemini up to 2 times on invalid quiz JSON before failing job")
    void processJob_QuizRetriesTwiceThenFails() {
        String jobId = "job-uuid-001";
        ClaimedJobSnapshot snapshot = new ClaimedJobSnapshot(
                jobId,
                10L,
                AiJobType.QUIZ,
                Map.of(
                        "courseId", 101L,
                        "materialIds", List.of(401L),
                        "title", "Quiz Test",
                        "questionCount", 1,
                        "difficulty", "MEDIUM",
                        "type", "SINGLE_CHOICE"
                )
        );
        ChunkCandidate chunk = new ChunkCandidate(
                884L, 401L, "Slide.pdf", 1, 2, "TCP là giao thức hướng kết nối.", 0
        );

        when(aiJobTxService.claimPendingJob(jobId)).thenReturn(Optional.of(snapshot));
        when(aiJobTxService.loadChunks(List.of(401L))).thenReturn(List.of(chunk));
        when(geminiClientService.generateStructuredJson(anyString(), anyString(), any()))
                .thenReturn("{ \"questions\": [] }");

        worker.processJob(jobId);

        verify(geminiClientService, times(3)).generateStructuredJson(anyString(), anyString(), any());
        verify(aiJobTxService).failJob(eq(jobId), eq(ErrorCode.AI_OUTPUT_INVALID.name()), anyString());
    }

    @Test
    @DisplayName("processJob generates and persists flashcards when FLASHCARD job succeeds")
    void processJob_Flashcard_GeneratesAndSavesCards() {
        String jobId = "job-uuid-fc-1";
        ClaimedJobSnapshot snapshot = new ClaimedJobSnapshot(
                jobId,
                10L,
                AiJobType.FLASHCARD,
                Map.of(
                        "materialIds", List.of(401L),
                        "deckTitle", "Flashcards TCP",
                        "cardCount", 2
                )
        );
        ChunkCandidate chunk = new ChunkCandidate(
                884L, 401L, "Slide.pdf", 1, 2, "TCP là giao thức hướng kết nối.", 0
        );

        when(aiJobTxService.claimPendingJob(jobId)).thenReturn(Optional.of(snapshot));
        when(aiJobTxService.loadChunks(List.of(401L))).thenReturn(List.of(chunk));
        when(geminiClientService.generateStructuredJson(anyString(), anyString(), any()))
                .thenReturn("""
                        {
                            "cards": [
                                {
                                    "front": "TCP là gì?",
                                    "back": "Giao thức hướng kết nối tin cậy.",
                                    "sourceChunkId": 884
                                }
                            ]
                        }
                        """);

        worker.processJob(jobId);

        verify(aiJobTxService).completeFlashcardJob(
                eq(jobId),
                eq(null),
                eq(null),
                eq("Flashcards TCP"),
                any()
        );
    }
}
