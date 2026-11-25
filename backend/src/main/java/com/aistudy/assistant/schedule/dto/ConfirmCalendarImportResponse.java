package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.schedule.entity.CalendarImportStatus;

import java.util.List;

public record ConfirmCalendarImportResponse(
        Long importId,
        CalendarImportStatus status,
        int importedCount,
        int skippedCount,
        List<Long> createdEventIds
) {
}
