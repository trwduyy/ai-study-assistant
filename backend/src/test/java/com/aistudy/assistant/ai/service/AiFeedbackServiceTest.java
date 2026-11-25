package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.admin.entity.Report;
import com.aistudy.assistant.admin.entity.ReportStatus;
import com.aistudy.assistant.admin.repository.ReportRepository;
import com.aistudy.assistant.ai.dto.AiFeedbackResponse;
import com.aistudy.assistant.ai.dto.CreateAiFeedbackRequest;
import com.aistudy.assistant.ai.entity.AiFeedback;
import com.aistudy.assistant.ai.entity.AiSummary;
import com.aistudy.assistant.ai.entity.FeedbackStatus;
import com.aistudy.assistant.ai.entity.FeedbackTargetType;
import com.aistudy.assistant.ai.mapper.AiMapper;
import com.aistudy.assistant.ai.repository.AiChatMessageRepository;
import com.aistudy.assistant.ai.repository.AiFeedbackRepository;
import com.aistudy.assistant.ai.repository.AiSummaryRepository;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.learning.repository.MaterialRepository;
import com.aistudy.assistant.learning.repository.QuizRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiFeedbackServiceTest {

    @Mock
    private AiFeedbackRepository aiFeedbackRepository;
    @Mock
    private ReportRepository reportRepository;
    @Mock
    private AiChatMessageRepository aiChatMessageRepository;
    @Mock
    private AiSummaryRepository aiSummaryRepository;
    @Mock
    private QuizRepository quizRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private UserRepository userRepository;

    private AiFeedbackServiceImpl aiFeedbackService;
    private User studentUser;

    @BeforeEach
    void setUp() {
        aiFeedbackService = new AiFeedbackServiceImpl(
                aiFeedbackRepository,
                reportRepository,
                aiChatMessageRepository,
                aiSummaryRepository,
                quizRepository,
                materialRepository,
                userRepository,
                new AiMapper()
        );
        studentUser = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Sinh Viên A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();
        UserPrincipal principal = UserPrincipal.create(studentUser);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("submitFeedback persists AiFeedback and mirrors an AI_INACCURATE Report for Admin review")
    void submitFeedback_AlsoCreatesAiInaccurateReport() {
        AiSummary summary = AiSummary.builder().id(50L).user(studentUser).build();
        when(aiSummaryRepository.findWithDetailsById(50L)).thenReturn(Optional.of(summary));
        when(userRepository.getReferenceById(11L)).thenReturn(studentUser);
        when(aiFeedbackRepository.save(any(AiFeedback.class))).thenAnswer(inv -> {
            AiFeedback f = inv.getArgument(0);
            f.setId(701L);
            return f;
        });

        AiFeedbackResponse response = aiFeedbackService.submitFeedback(
                new CreateAiFeedbackRequest(FeedbackTargetType.AI_SUMMARY, 50L, "Tóm tắt thiếu ý chính")
        );

        assertEquals(701L, response.id());
        assertEquals(FeedbackStatus.OPEN, response.status());

        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).save(reportCaptor.capture());
        Report mirrored = reportCaptor.getValue();
        assertEquals("AI_INACCURATE", mirrored.getType());
        assertEquals("AI_SUMMARY", mirrored.getTargetType());
        assertEquals(50L, mirrored.getTargetId());
        assertEquals("Tóm tắt thiếu ý chính", mirrored.getDetail());
        assertEquals(ReportStatus.OPEN, mirrored.getStatus());
    }
}
