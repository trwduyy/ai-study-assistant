package com.aistudy.assistant.ai.service;

import com.aistudy.assistant.ai.dto.AiJobAcceptedResponse;
import com.aistudy.assistant.ai.dto.AiJobCancelResponse;
import com.aistudy.assistant.ai.dto.AiJobResponse;
import com.aistudy.assistant.ai.dto.AiSummaryResponse;
import com.aistudy.assistant.ai.dto.CreateExplanationRequest;
import com.aistudy.assistant.ai.dto.CreateSummaryRequest;
import com.aistudy.assistant.ai.dto.GenerateAiQuizRequest;

import java.util.List;

public interface AiJobService {

    AiJobResponse getJob(String jobId);

    AiJobCancelResponse cancelJob(String jobId);

    AiJobAcceptedResponse requestSummary(CreateSummaryRequest request);

    List<AiSummaryResponse> listMaterialSummaries(Long materialId);

    AiSummaryResponse getSummary(Long summaryId);

    void deleteSummary(Long summaryId);

    AiJobAcceptedResponse requestExplanation(CreateExplanationRequest request);

    AiJobAcceptedResponse requestQuizGeneration(GenerateAiQuizRequest request);
}
