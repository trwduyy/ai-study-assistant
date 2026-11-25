package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventStatus;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Shared validation and normalization helpers for calendar/meeting events.
 */
public final class EventValidationSupport {

    public static final String DEFAULT_TIMEZONE = "Asia/Ho_Chi_Minh";

    private EventValidationSupport() {
    }

    public static void validateTimeWindow(Instant startAt, Instant endAt) {
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thời gian kết thúc sự kiện phải sau thời gian bắt đầu.",
                    List.of(new FieldErrorDetail("endAt", "Thời gian kết thúc phải lớn hơn thời gian bắt đầu."))
            );
        }
    }

    public static String normalizeAndValidateTimezone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return DEFAULT_TIMEZONE;
        }
        String trimmed = timezone.trim();
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (DateTimeException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Múi giờ không hợp lệ.",
                    List.of(new FieldErrorDetail("timezone", "Múi giờ IANA không hợp lệ (ví dụ: Asia/Ho_Chi_Minh)."))
            );
        }
    }

    public static String normalizeAndValidateMeetingUrl(String meetingUrl) {
        String trimmed = trimToNull(meetingUrl);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > 500) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đường dẫn cuộc họp vượt quá 500 ký tự.",
                    List.of(new FieldErrorDetail("meetingUrl", "URL cuộc họp tối đa 500 ký tự."))
            );
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null
                    || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                    || !StringUtils.hasText(host)) {
                throw new IllegalArgumentException("Invalid scheme or host");
            }
            return trimmed;
        } catch (IllegalArgumentException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Đường dẫn cuộc họp không hợp lệ.",
                    List.of(new FieldErrorDetail("meetingUrl", "URL cuộc họp phải bắt đầu bằng http:// hoặc https:// và đúng định dạng."))
            );
        }
    }

    public static EventStatus computeEffectiveStatus(Event event, Instant now) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            return EventStatus.CANCELLED;
        }
        if (now.isBefore(event.getStartAt())) {
            return EventStatus.UPCOMING;
        }
        if (!now.isAfter(event.getEndAt())) {
            return EventStatus.ONGOING;
        }
        return EventStatus.ENDED;
    }

    public static Integer resolveExpectedVersion(Integer ifMatchVersion, Integer bodyVersion) {
        Integer expectedVersion = ifMatchVersion != null ? ifMatchVersion : bodyVersion;
        if (expectedVersion == null) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Thiếu thông tin version để cập nhật sự kiện.",
                    List.of(new FieldErrorDetail("version", "Vui lòng cung cấp version trong body hoặc header If-Match."))
            );
        }
        return expectedVersion;
    }

    public static Integer parseIfMatchVersion(String ifMatchHeader) {
        if (!StringUtils.hasText(ifMatchHeader)) {
            return null;
        }
        String sanitized = ifMatchHeader.trim();
        if (sanitized.startsWith("W/")) {
            sanitized = sanitized.substring(2).trim();
        }
        if (sanitized.startsWith("\"") && sanitized.endsWith("\"") && sanitized.length() >= 2) {
            sanitized = sanitized.substring(1, sanitized.length() - 1).trim();
        }
        try {
            return Integer.parseInt(sanitized);
        } catch (NumberFormatException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Header If-Match không hợp lệ.");
        }
    }

    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public static String normalizeProvider(String provider) {
        return provider == null ? null : provider.trim().toUpperCase(Locale.ROOT);
    }
}
