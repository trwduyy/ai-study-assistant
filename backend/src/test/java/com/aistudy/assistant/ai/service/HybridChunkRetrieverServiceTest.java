package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.entity.MaterialChunkEmbedding;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.repository.MaterialChunkEmbeddingRepository;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.learning.entity.MaterialChunk;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HybridChunkRetrieverServiceTest {

    @Mock
    private MaterialChunkEmbeddingRepository embeddingRepository;
    @Mock
    private GeminiClientService geminiClientService;

    private HybridChunkRetrieverServiceImpl retrieverService;

    @BeforeEach
    void setUp() {
        retrieverService = new HybridChunkRetrieverServiceImpl(
                embeddingRepository,
                geminiClientService,
                new ChunkGroundingService(),
                new ObjectMapper(),
                0.45
        );
    }

    @Test
    @DisplayName("cosineSimilarity computes exact values for identical, orthogonal, and invalid vectors")
    void cosineSimilarity_ComputesAccurately() {
        assertEquals(1.0, retrieverService.cosineSimilarity(new float[]{1f, 0f, 0f}, new float[]{2f, 0f, 0f}), 1e-6);
        assertEquals(0.0, retrieverService.cosineSimilarity(new float[]{1f, 0f, 0f}, new float[]{0f, 1f, 0f}), 1e-6);
        assertEquals(-1.0, retrieverService.cosineSimilarity(new float[]{1f, 0f}, new float[]{-1f, 0f}), 1e-6);
        assertEquals(0.0, retrieverService.cosineSimilarity(new float[]{1f}, new float[]{1f, 2f}), 1e-6);
        assertEquals(0.0, retrieverService.cosineSimilarity(null, new float[]{1f}), 1e-6);
    }

    @Test
    @DisplayName("retrieveRelevantChunks returns empty list when off-topic query scores below minScore threshold")
    void retrieveRelevantChunks_OffTopicQueryBelowThreshold_ReturnsEmpty() {
        ChunkCandidate tcpChunk = new ChunkCandidate(
                101L, 1L, "Mang-may-tinh.pdf", 5, 5,
                "TCP sử dụng bắt tay ba bước SYN SYN-ACK ACK để thiết lập kết nối.", 0
        );
        MaterialChunkEmbedding tcpEmbedding = MaterialChunkEmbedding.builder()
                .chunk(MaterialChunk.builder().id(101L).build())
                .embeddingJson("[1.0, 0.0, 0.0]")
                .build();

        when(geminiClientService.isConfigured()).thenReturn(true);
        when(embeddingRepository.findByChunkIdIn(List.of(101L))).thenReturn(List.of(tcpEmbedding));
        when(geminiClientService.embedText("Công thức nấu phở bò Hà Nội như thế nào?"))
                .thenReturn(new float[]{0.0f, 1.0f, 0.0f});

        List<ChunkCandidate> result = retrieverService.retrieveRelevantChunks(
                "Công thức nấu phở bò Hà Nội như thế nào?",
                List.of(tcpChunk),
                5
        );

        assertTrue(result.isEmpty(), "Off-topic query with cosine=0 and keyword=0 must not pass minScore=0.45");
    }

    @Test
    @DisplayName("retrieveRelevantChunks ranks hybrid semantic + keyword matches above minScore")
    void retrieveRelevantChunks_RelevantQuery_ReturnsRankedMatches() {
        ChunkCandidate c1 = new ChunkCandidate(
                101L, 1L, "Mang-may-tinh.pdf", 2, 2,
                "Giao thức UDP không kết nối và truyền datagram nhanh.", 0
        );
        ChunkCandidate c2 = new ChunkCandidate(
                102L, 1L, "Mang-may-tinh.pdf", 4, 4,
                "TCP kiểm soát tắc nghẽn bằng Slow Start và Congestion Avoidance.", 1
        );

        MaterialChunkEmbedding emb1 = MaterialChunkEmbedding.builder()
                .chunk(MaterialChunk.builder().id(101L).build())
                .embeddingJson("[0.1, 0.9, 0.0]")
                .build();
        MaterialChunkEmbedding emb2 = MaterialChunkEmbedding.builder()
                .chunk(MaterialChunk.builder().id(102L).build())
                .embeddingJson("[0.95, 0.05, 0.0]")
                .build();

        when(geminiClientService.isConfigured()).thenReturn(true);
        when(embeddingRepository.findByChunkIdIn(List.of(101L, 102L))).thenReturn(List.of(emb1, emb2));
        when(geminiClientService.embedText("TCP kiểm soát tắc nghẽn bằng cơ chế nào?"))
                .thenReturn(new float[]{1.0f, 0.0f, 0.0f});

        List<ChunkCandidate> result = retrieverService.retrieveRelevantChunks(
                "TCP kiểm soát tắc nghẽn bằng cơ chế nào?",
                List.of(c1, c2),
                5
        );

        assertEquals(1, result.size());
        assertEquals(102L, result.get(0).chunkId());
    }
}
