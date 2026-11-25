package com.aistudy.assistant.schedule.dto;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Supported calendar range view modes.
 */
public enum CalendarView {
    DAY(Duration.ofDays(1)),
    WEEK(Duration.ofDays(7)),
    MONTH(Duration.ofDays(31));

    private final Duration defaultDuration;

    CalendarView(Duration defaultDuration) {
        this.defaultDuration = defaultDuration;
    }

    public Duration defaultDuration() {
        return defaultDuration;
    }

    public static CalendarView parseOrDefault(String rawView) {
        if (rawView == null || rawView.isBlank()) {
            return WEEK;
        }
        try {
            return CalendarView.valueOf(rawView.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tham số view không hợp lệ.",
                    List.of(new FieldErrorDetail("view", "Giá trị hợp lệ: DAY, WEEK, MONTH."))
            );
        }
    }
}
