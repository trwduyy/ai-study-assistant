package com.aistudy.assistant.controller;

import com.aistudy.assistant.common.ApiResponse;
import com.aistudy.assistant.dto.summary.GenerateSummaryRequest;
import com.aistudy.assistant.dto.summary.SummaryResponse;
import com.aistudy.assistant.service.SummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "AI Summarization", description = "Endpoints for generating and retrieving AI-powered document summaries")
@SecurityRequirement(name = "BearerAuth")
public class SummaryController {

    private final SummaryService summaryService;

    @PostMapping("/summaries/generate")
    @Operation(summary = "Generate a structured AI summary of a study document")
    public ResponseEntity<ApiResponse<SummaryResponse>> generateSummary(
            @Valid @RequestBody GenerateSummaryRequest request) {
        SummaryResponse response = summaryService.generateSummary(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Summary generated successfully"));
    }

    @GetMapping("/materials/{materialId}/summaries")
    @Operation(summary = "Get all saved summaries for a specific material")
    public ResponseEntity<ApiResponse<List<SummaryResponse>>> getSummariesByMaterial(
            @PathVariable Long materialId) {
        List<SummaryResponse> summaries = summaryService.getSummariesByMaterial(materialId);
        return ResponseEntity.ok(ApiResponse.success(summaries, "Material summaries retrieved"));
    }

    @GetMapping("/summaries/{id}")
    @Operation(summary = "Get summary by ID")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummaryById(@PathVariable Long id) {
        SummaryResponse response = summaryService.getSummaryById(id);
        return ResponseEntity.ok(ApiResponse.success(response, "Summary retrieved"));
    }
}
