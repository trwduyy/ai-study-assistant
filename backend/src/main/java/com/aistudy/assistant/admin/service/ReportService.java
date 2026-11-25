package com.aistudy.assistant.admin.service;

import com.aistudy.assistant.admin.dto.CreateReportRequest;
import com.aistudy.assistant.admin.dto.ReportResponse;

/**
 * Handles user-submitted reports for inaccurate AI responses or abusive class/chat behavior (UC-59).
 */
public interface ReportService {

    ReportResponse createReport(CreateReportRequest request);
}
