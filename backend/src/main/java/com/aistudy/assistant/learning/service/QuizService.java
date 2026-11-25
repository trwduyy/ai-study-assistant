package com.aistudy.assistant.learning.service;

import com.aistudy.assistant.learning.dto.AttemptReviewResponse;
import com.aistudy.assistant.learning.dto.QuizAttemptSummaryResponse;
import com.aistudy.assistant.learning.dto.QuizDetailResponse;
import com.aistudy.assistant.learning.dto.QuizSubmitResultResponse;
import com.aistudy.assistant.learning.dto.QuizSummaryResponse;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftRequest;
import com.aistudy.assistant.learning.dto.SaveAttemptDraftResponse;
import com.aistudy.assistant.learning.dto.StartAttemptResponse;
import com.aistudy.assistant.learning.dto.SubmitAttemptRequest;
import com.aistudy.assistant.learning.dto.UpdateQuizRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface QuizService {

    Page<QuizSummaryResponse> listQuizzes(Long courseId, Pageable pageable);

    QuizDetailResponse getQuizForAttempt(Long quizId);

    QuizSummaryResponse updateQuiz(Long quizId, UpdateQuizRequest request);

    void deleteQuiz(Long quizId);

    StartAttemptResponse startAttempt(Long quizId);

    SaveAttemptDraftResponse saveAttemptDraft(Long quizId, Long attemptId, SaveAttemptDraftRequest request);

    QuizSubmitResultResponse submitAttempt(
            Long quizId,
            Long attemptId,
            String idempotencyKey,
            SubmitAttemptRequest request
    );

    Page<QuizAttemptSummaryResponse> listAttempts(Long quizId, Pageable pageable);

    AttemptReviewResponse getAttemptReview(Long attemptId);
}
