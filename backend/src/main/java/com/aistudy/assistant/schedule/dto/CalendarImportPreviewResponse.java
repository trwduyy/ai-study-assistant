package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.CalendarImportSourceType;
import com.aistudy.assistant.schedule.entity.CalendarImportStatus;

import java.util.List;

public record CalendarImportPreviewResponse(
        Long importId,
        CalendarImportSourceType sourceType,
        String fileName,
        String timezone,
        CalendarImportStatus status,
        int totalParsed,
        int conflictCount,
        List<CalendarImportItemDto> items
) {
}
