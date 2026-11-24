package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.quiz.AttemptDetailResponse;
import com.aistudy.assistant.dto.quiz.GenerateQuizRequest;
import com.aistudy.assistant.dto.quiz.QuizAttemptHistoryResponse;
import com.aistudy.assistant.dto.quiz.QuizExamResponse;
import com.aistudy.assistant.dto.quiz.QuizResponse;
import com.aistudy.assistant.dto.quiz.QuizSubmitResultResponse;
import com.aistudy.assistant.dto.quiz.SubmitQuizRequest;

import java.util.List;

public interface QuizService {

    QuizResponse generateQuiz(GenerateQuizRequest request);

    List<QuizResponse> getQuizzesByCourse(Long courseId);

    QuizExamResponse getQuizExamDetail(Long quizId);

    QuizSubmitResultResponse submitQuiz(Long quizId, SubmitQuizRequest request);

    AttemptDetailResponse getAttemptDetail(Long attemptId);

    List<QuizAttemptHistoryResponse> getQuizAttempts(Long quizId);
}
