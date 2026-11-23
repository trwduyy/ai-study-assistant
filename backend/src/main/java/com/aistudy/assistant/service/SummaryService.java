package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.summary.GenerateSummaryRequest;
import com.aistudy.assistant.dto.summary.SummaryResponse;

import java.util.List;

public interface SummaryService {

    SummaryResponse generateSummary(GenerateSummaryRequest request);

    List<SummaryResponse> getSummariesByMaterial(Long materialId);

    SummaryResponse getSummaryById(Long id);
}
