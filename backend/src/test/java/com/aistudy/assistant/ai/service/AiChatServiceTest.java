package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.admin.service.AiQuotaService;
import com.aistudy.assistant.ai.dto.AiChatAnswerResponse;
import com.aistudy.assistant.ai.dto.SendAiChatMessageRequest;
import com.aistudy.assistant.ai.entity.AiMessageRole;
import com.aistudy.assistant.ai.integration.GeminiClientService;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiChatRepository;
import com.aistudy.assistant.ai.repository.AiMessageSourceRepository;
import com.aistudy.assistant.ai.service.AiChatTxService.ChatHistoryTurn;
import com.aistudy.assistant.ai.service.AiChatTxService.PreparedChatTurn;
import com.aistudy.assistant.ai.service.ChunkGroundingService.ChunkCandidate;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.repository.CourseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiChatServiceTest {

    @Mock
    private AiChatRepository aiChatRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private AiMessageSourceRepository aiMessageSourceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private AiChatTxService aiChatTxService;
    @Mock
    private GeminiClientService geminiClientService;
    @Mock
    private com.aistudy.assistant.ai.repository.MaterialChunkEmbeddingRepository embeddingRepository;
    @Mock
    private AiQuotaService aiQuotaService;

    private AiChatServiceImpl aiChatService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        ChunkRetrieverService retrieverService = new HybridChunkRetrieverServiceImpl(
                embeddingRepository,
                geminiClientService,
                new ChunkGroundingService(),
                objectMapper,
                0.45
        );
        aiChatService = new AiChatServiceImpl(
                aiChatRepository,
                aiChatMessageRepository,
                aiMessageSourceRepository,
                userRepository,
                courseRepository,
                aiChatTxService,
                retrieverService,
                geminiClientService,
                new AiMapper(),
                objectMapper,
                aiQuotaService
        );

        User student = User.builder()
                .id(10L)
                .email("student@aistudy.local")
                .passwordHash("hash")
                .fullName("Sinh Viên")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        UserPrincipal principal = UserPrincipal.create(student);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("sendMessage returns fixed not-enough-info response with grounded=false when no chunk matches")
    void sendMessage_ReturnsNotEnoughInfoWhenNoMatchingChunk() {
        ChunkCandidate unrelatedChunk = new ChunkCandidate(
                880L,
                401L,
                "Giai-thuat.pdf",
                3,
                4,
                "Thuật toán QuickSort chia để trị chọn pivot để phân hoạch mảng.",
                0
        );
        PreparedChatTurn prepared = new PreparedChatTurn(
                501L,
                List.of(unrelatedChunk),
                List.of(new ChatHistoryTurn(AiMessageRole.USER, "Chào AI"))
        );

        when(aiChatTxService.prepareAndSaveUserTurn(eq(501L), eq(10L), anyString(), any()))
                .thenReturn(prepared);
        when(geminiClientService.getModel()).thenReturn("gemini-4-hydrogen");
        when(aiChatTxService.saveAssistantTurn(
                eq(501L),
                eq(AiChatServiceImpl.NOT_ENOUGH_INFO_ANSWER),
                eq(false),
                eq(List.of()),
                anyString(),
                eq("gemini-4-hydrogen"),
                eq(AiChatServiceImpl.PROMPT_VERSION_CHAT)))
                .thenReturn(new AiChatAnswerResponse(
                        5523L,
                        AiChatServiceImpl.NOT_ENOUGH_INFO_ANSWER,
                        false,
                        List.of(),
                        "Bạn có thể bật tùy chọn tham khảo kiến thức ngoài để AI giải thích mở rộng.",
                        "gemini-4-hydrogen",
                        AiChatServiceImpl.PROMPT_VERSION_CHAT,
                        Instant.now()
                ));

        AiChatAnswerResponse response = aiChatService.sendMessage(
                501L,
                new SendAiChatMessageRequest("Giao thức OSPF định tuyến trạng thái liên kết như thế nào?", List.of(401L), false)
        );

        assertFalse(response.grounded());
        assertEquals(AiChatServiceImpl.NOT_ENOUGH_INFO_ANSWER, response.answer());
        assertTrue(response.sources().isEmpty());
        verify(geminiClientService, never()).generateStructuredJson(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("sendMessage cites matching chunks and keeps grounded=true when evidence exists")
    void sendMessage_ReturnsGroundedAnswerWithCitations() {
        ChunkCandidate tcpChunk = new ChunkCandidate(
                884L,
                401L,
                "Mang-may-tinh-chuong-3.pdf",
                14,
                14,
                "TCP là giao thức hướng kết nối, đảm bảo độ tin cậy và kiểm soát luồng bằng Sliding Window.",
                3
        );
        PreparedChatTurn prepared = new PreparedChatTurn(501L, List.of(tcpChunk), List.of());

        when(aiChatTxService.prepareAndSaveUserTurn(eq(501L), eq(10L), anyString(), any()))
                .thenReturn(prepared);
        when(geminiClientService.getModel()).thenReturn("gemini-4-hydrogen");
        when(geminiClientService.generateStructuredJson(anyString(), anyString(), any()))
                .thenReturn("""
                        {
                            "grounded": true,
                            "answer": "Theo tài liệu, TCP là giao thức hướng kết nối và dùng Sliding Window để kiểm soát luồng.",
                            "citedChunkIds": [884],
                            "aiAddedNote": null
                        }
                        """);
        when(aiChatTxService.saveAssistantTurn(
                eq(501L),
                anyString(),
                eq(true),
                anyList(),
                any(),
                eq("gemini-4-hydrogen"),
                eq(AiChatServiceImpl.PROMPT_VERSION_CHAT)))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    List<ChunkCandidate> chunks = inv.getArgument(3);
                    assertEquals(1, chunks.size());
                    assertEquals(884L, chunks.get(0).chunkId());
                    return new AiChatAnswerResponse(
                            5521L,
                            inv.getArgument(1),
                            true,
                            List.of(new com.aistudy.assistant.ai.dto.AiSourceCitationResponse(
                                    401L, "Mang-may-tinh-chuong-3.pdf", 14, 884L)),
                            null,
                            "gemini-4-hydrogen",
                            AiChatServiceImpl.PROMPT_VERSION_CHAT,
                            Instant.now()
                    );
                });

        AiChatAnswerResponse response = aiChatService.sendMessage(
                501L,
                new SendAiChatMessageRequest("TCP kiểm soát luồng bằng cơ chế nào?", List.of(401L), false)
        );

        assertTrue(response.grounded());
        assertEquals(1, response.sources().size());
        assertEquals(884L, response.sources().get(0).chunkId());
        assertEquals(14, response.sources().get(0).page());
        verify(aiChatTxService).saveAssistantTurn(any(), anyString(), anyBoolean(), anyList(), any(), anyString(), anyString());
    }
}
