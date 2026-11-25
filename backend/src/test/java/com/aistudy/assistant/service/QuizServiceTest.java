package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.quiz.AttemptDetailResponse;
import com.aistudy.assistant.dto.quiz.GenerateQuizRequest;
import com.aistudy.assistant.dto.quiz.QuestionAnswerSubmit;
import com.aistudy.assistant.dto.quiz.QuizExamResponse;
import com.aistudy.assistant.dto.quiz.QuizResponse;
import com.aistudy.assistant.dto.quiz.QuizSubmitResultResponse;
import com.aistudy.assistant.dto.quiz.SubmitQuizRequest;
import com.aistudy.assistant.entity.AttemptAnswer;
import com.aistudy.assistant.entity.Course;
import com.aistudy.assistant.entity.Difficulty;
import com.aistudy.assistant.entity.ExtractionStatus;
import com.aistudy.assistant.entity.Material;
import com.aistudy.assistant.entity.OptionLetter;
import com.aistudy.assistant.entity.Quiz;
import com.aistudy.assistant.entity.QuizAttempt;
import com.aistudy.assistant.entity.QuizQuestion;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.repository.AttemptAnswerRepository;
import com.aistudy.assistant.repository.CourseRepository;
import com.aistudy.assistant.repository.MaterialRepository;
import com.aistudy.assistant.repository.QuizAttemptRepository;
import com.aistudy.assistant.repository.QuizQuestionRepository;
import com.aistudy.assistant.repository.QuizRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.service.impl.QuizServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private QuizAttemptRepository quizAttemptRepository;

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GeminiClientService geminiClientService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private QuizServiceImpl quizService;

    private User mockUser;
    private Course mockCourse;
    private Quiz mockQuiz;
    private List<QuizQuestion> mockQuestions;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .email("student01@example.com")
                .fullName("Nguyen Van A")
                .passwordHash("hash")
                .build();

        UserPrincipal principal = UserPrincipal.create(mockUser);
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        mockCourse = Course.builder()
                .id(10L)
                .user(mockUser)
                .title("Database Systems")
                .build();

        mockQuiz = Quiz.builder()
                .id(100L)
                .user(mockUser)
                .course(mockCourse)
                .title("SQL Basics Quiz")
                .difficulty(Difficulty.MEDIUM)
                .questionCount(2)
                .build();

        mockQuestions = new ArrayList<>();
        mockQuestions.add(QuizQuestion.builder()
                .id(1001L)
                .quiz(mockQuiz)
                .orderIndex(1)
                .questionText("What does SQL stand for?")
                .optionA("Strong Question Language")
                .optionB("Structured Query Language")
                .optionC("Simple Query List")
                .optionD("None of the above")
                .correctOption(OptionLetter.B)
                .explanation("SQL stands for Structured Query Language.")
                .build());

        mockQuestions.add(QuizQuestion.builder()
                .id(1002L)
                .quiz(mockQuiz)
                .orderIndex(2)
                .questionText("Which clause filters grouped records in SQL?")
                .optionA("WHERE")
                .optionB("ORDER BY")
                .optionC("HAVING")
                .optionD("GROUP BY")
                .correctOption(OptionLetter.C)
                .explanation("HAVING is used to filter after GROUP BY aggregation.")
                .build());

        mockQuiz.setQuestions(mockQuestions);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should accurately score submitted quiz answers on a 100-point scale")
    void submitQuiz_ScoresAccuratelyOn100Scale() {
        SubmitQuizRequest request = SubmitQuizRequest.builder()
                .answers(List.of(
                        new QuestionAnswerSubmit(1001L, OptionLetter.B), // Correct
                        new QuestionAnswerSubmit(1002L, OptionLetter.A)  // Wrong (Correct is C)
                ))
                .build();

        when(quizRepository.findWithQuestionsByIdAndUserId(100L, 1L)).thenReturn(Optional.of(mockQuiz));

        when(quizAttemptRepository.save(any(QuizAttempt.class))).thenAnswer(invocation -> {
            QuizAttempt attempt = invocation.getArgument(0);
            if (attempt.getId() == null) {
                attempt.setId(500L);
            }
            return attempt;
        });

        QuizSubmitResultResponse response = quizService.submitQuiz(100L, request);

        assertNotNull(response);
        assertEquals(500L, response.getAttemptId());
        assertEquals(100L, response.getQuizId());
        assertEquals(2, response.getTotalQuestions());
        assertEquals(1, response.getCorrectCount());
        assertEquals(new BigDecimal("50.00"), response.getScore());

        verify(attemptAnswerRepository).saveAll(any());
        verify(quizAttemptRepository, times(2)).save(any(QuizAttempt.class));
    }

    @Test
    @DisplayName("Exam detail must not leak correctOption or explanation to the test taker")
    void getQuizExamDetail_HidesCorrectOptionAndExplanation() {
        when(quizRepository.findWithQuestionsByIdAndUserId(100L, 1L)).thenReturn(Optional.of(mockQuiz));

        QuizExamResponse examDetail = quizService.getQuizExamDetail(100L);

        assertNotNull(examDetail);
        assertEquals(100L, examDetail.getId());
        assertEquals(2, examDetail.getQuestions().size());

        // Verify that the exam question DTO contains questions and choices,
        // and cannot provide correctOption or explanation fields
        assertEquals("What does SQL stand for?", examDetail.getQuestions().get(0).getQuestionText());
        assertEquals("Structured Query Language", examDetail.getQuestions().get(0).getOptionB());
    }

    @Test
    @DisplayName("Should retrieve attempt details with answers and explanations via optimized fetch")
    void getAttemptDetail_ReturnsFullReview() {
        QuizAttempt mockAttempt = QuizAttempt.builder()
                .id(500L)
                .quiz(mockQuiz)
                .user(mockUser)
                .score(new BigDecimal("50.00"))
                .totalQuestions(2)
                .correctCount(1)
                .submittedAt(Instant.now())
                .answers(List.of(
                        AttemptAnswer.builder()
                                .id(1L)
                                .question(mockQuestions.get(0))
                                .selectedOption(OptionLetter.B)
                                .isCorrect(true)
                                .build()
                ))
                .build();

        when(quizAttemptRepository.findWithDetailsByIdAndUserId(500L, 1L)).thenReturn(Optional.of(mockAttempt));

        AttemptDetailResponse detail = quizService.getAttemptDetail(500L);

        assertNotNull(detail);
        assertEquals(500L, detail.getAttemptId());
        assertEquals("SQL Basics Quiz", detail.getQuizTitle());
        assertEquals(1, detail.getAnswers().size());
        assertEquals(OptionLetter.B, detail.getAnswers().get(0).getCorrectOption());
    }

    @Test
    @DisplayName("Should successfully generate questions from AI structured JSON response")
    void generateQuiz_ParsesJsonFromAiProperly() {
        String mockAiJson = """
                [
                    {
                        "questionText": "What is an index in MySQL?",
                        "optionA": "A data structure improving query speed",
                        "optionB": "A database trigger",
                        "optionC": "A backup tool",
                        "optionD": "A network protocol",
                        "correctOption": "A",
                        "explanation": "Indexes speed up data retrieval operations."
                    }
                ]
                """;

        Material mockMaterial = Material.builder()
                .id(20L)
                .course(mockCourse)
                .user(mockUser)
                .originalName("indexing.pdf")
                .extractionStatus(ExtractionStatus.SUCCESS)
                .extractedText("Indexes allow database queries to search without scanning entire tables...")
                .build();

        GenerateQuizRequest request = GenerateQuizRequest.builder()
                .courseId(10L)
                .materialId(20L)
                .title("Indexing Quiz")
                .difficulty(Difficulty.EASY)
                .questionCount(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(mockUser));
        when(courseRepository.findByIdAndUserId(10L, 1L)).thenReturn(Optional.of(mockCourse));
        when(materialRepository.findByIdAndUserId(20L, 1L)).thenReturn(Optional.of(mockMaterial));
        when(geminiClientService.generateStructuredJson(anyString(), anyString(), any())).thenReturn(mockAiJson);
        when(quizRepository.save(any(Quiz.class))).thenAnswer(invocation -> {
            Quiz q = invocation.getArgument(0);
            q.setId(200L);
            q.setCreatedAt(Instant.now());
            return q;
        });

        QuizResponse response = quizService.generateQuiz(request);

        assertNotNull(response);
        assertEquals(200L, response.getId());
        assertEquals("Indexing Quiz", response.getTitle());
        verify(quizQuestionRepository).saveAll(any());
    }
}
