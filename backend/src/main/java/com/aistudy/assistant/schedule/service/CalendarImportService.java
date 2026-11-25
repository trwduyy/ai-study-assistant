package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.schedule.dto.CalendarImportPreviewResponse;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportRequest;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportResponse;
import org.springframework.web.multipart.MultipartFile;

public interface CalendarImportService {

    byte[] getImportTemplateCsv();

    CalendarImportPreviewResponse previewImport(MultipartFile file, String defaultTimezone);

    ConfirmCalendarImportResponse confirmImport(Long importId, ConfirmCalendarImportRequest request);
}
