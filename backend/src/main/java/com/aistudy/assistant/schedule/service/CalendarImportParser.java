package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.schedule.dto.StoredImportPreviewItem;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Date;
import net.fortuna.ical4j.model.DateTime;
import net.fortuna.ical4j.model.Property;
import net.fortuna.ical4j.model.component.CalendarComponent;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.DtEnd;
import net.fortuna.ical4j.model.property.DtStart;
import net.fortuna.ical4j.model.property.Duration;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Stateless parser for {@code .ics} (iCalendar via iCal4j) and UTF-8 {@code .csv} schedules.
 * Normalizes all parsed timestamps to UTC {@link Instant} while preserving the original IANA timezone.
 */
@Component
public class CalendarImportParser {

    static {
        System.setProperty("net.fortuna.ical4j.timezone.cache.impl", "net.fortuna.ical4j.util.MapTimeZoneCache");
        System.setProperty("ical4j.unfolding.relaxed", "true");
        System.setProperty("ical4j.parsing.relaxed", "true");
    }

    private static final DateTimeFormatter ICS_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ICS_LOCAL_DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final List<DateTimeFormatter> CSV_LOCAL_DATETIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    );

    public List<StoredImportPreviewItem> parseIcs(byte[] content, String defaultTimezone) {
        if (content == null || content.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tệp .ics rỗng.");
        }
        String normalizedDefaultTz = EventValidationSupport.normalizeAndValidateTimezone(defaultTimezone);

        Calendar calendar;
        try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
            CalendarBuilder builder = new CalendarBuilder();
            calendar = builder.build(reader);
        } catch (IOException | ParserException | RuntimeException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Tệp .ics không đúng cú pháp hoặc không thể phân tích: " + ex.getMessage()
            );
        }

        List<CalendarComponent> vEvents = calendar.getComponents(net.fortuna.ical4j.model.Component.VEVENT);
        if (vEvents == null || vEvents.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tệp .ics không chứa sự kiện VEVENT nào.");
        }

        List<StoredImportPreviewItem> items = new ArrayList<>();
        for (int i = 0; i < vEvents.size(); i++) {
            VEvent vEvent = (VEvent) vEvents.get(i);
            int eventNumber = i + 1;

            String title = propertyValue(vEvent, Property.SUMMARY);
            if (!StringUtils.hasText(title)) {
                throw rowError("event " + eventNumber, "Sự kiện thứ " + eventNumber + " thiếu tiêu đề (SUMMARY).");
            }
            if (title.trim().length() > 255) {
                throw rowError("event " + eventNumber, "Tiêu đề sự kiện thứ " + eventNumber + " vượt quá 255 ký tự.");
            }

            DtStart dtStart = vEvent.getStartDate();
            if (dtStart == null || dtStart.getDate() == null) {
                throw rowError("event " + eventNumber, "Sự kiện thứ " + eventNumber + " thiếu thời gian bắt đầu (DTSTART).");
            }

            String eventTimezone = resolveIcsTimezone(dtStart, normalizedDefaultTz);
            ZoneId zoneId = ZoneId.of(eventTimezone);
            Instant startAt = toUtcInstant(dtStart.getDate(), zoneId);

            Instant endAt = resolveIcsEndInstant(vEvent, dtStart, startAt, zoneId, eventNumber);
            if (!endAt.isAfter(startAt)) {
                throw rowError("event " + eventNumber, "Sự kiện thứ " + eventNumber + " có thời gian kết thúc không sau thời gian bắt đầu.");
            }

            String externalId = truncateNullable(propertyValue(vEvent, Property.UID), 255);
            String description = EventValidationSupport.trimToNull(propertyValue(vEvent, Property.DESCRIPTION));
            String location = EventValidationSupport.trimToNull(propertyValue(vEvent, Property.LOCATION));

            items.add(new StoredImportPreviewItem(
                    i,
                    externalId,
                    title.trim(),
                    description,
                    location,
                    startAt,
                    endAt,
                    eventTimezone
            ));
        }
        return items;
    }

    public List<StoredImportPreviewItem> parseCsv(byte[] content, String defaultTimezone) {
        if (content == null || content.length == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tệp .csv rỗng.");
        }
        String normalizedDefaultTz = EventValidationSupport.normalizeAndValidateTimezone(defaultTimezone);

        List<StoredImportPreviewItem> items = new ArrayList<>();
        try (BufferedReader bufferedReader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8));
             CSVReader csvReader = new CSVReaderBuilder(bufferedReader)
                     .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                     .build()) {

            String[] headers = csvReader.readNext();
            if (headers == null || headers.length == 0) {
                throw rowError("line 1", "Dòng 1: Tệp CSV thiếu dòng tiêu đề.");
            }

            Map<String, Integer> columnIndex = mapCsvHeaders(headers);
            validateRequiredCsvColumns(columnIndex);

            String[] row;
            int lineNumber = 1;
            int itemIndex = 0;
            while ((row = csvReader.readNext()) != null) {
                lineNumber++;
                if (isBlankRow(row)) {
                    continue;
                }

                String title = readCell(row, columnIndex.get("title"));
                if (!StringUtils.hasText(title)) {
                    throw rowError("line " + lineNumber, "Dòng " + lineNumber + ": Thiếu tiêu đề sự kiện (title).");
                }
                if (title.trim().length() > 255) {
                    throw rowError("line " + lineNumber, "Dòng " + lineNumber + ": Tiêu đề vượt quá 255 ký tự.");
                }

                String rawTimezone = readCell(row, columnIndex.get("timezone"));
                String eventTimezone;
                try {
                    eventTimezone = StringUtils.hasText(rawTimezone)
                            ? EventValidationSupport.normalizeAndValidateTimezone(rawTimezone)
                            : normalizedDefaultTz;
                } catch (AppException ex) {
                    throw rowError("line " + lineNumber, "Dòng " + lineNumber + ": Múi giờ không hợp lệ (" + rawTimezone + ").");
                }
                ZoneId zoneId = ZoneId.of(eventTimezone);

                String rawStartAt = readCell(row, columnIndex.get("start_at"));
                String rawEndAt = readCell(row, columnIndex.get("end_at"));
                Instant startAt = parseCsvTimestamp(rawStartAt, zoneId, lineNumber, "start_at");
                Instant endAt = parseCsvTimestamp(rawEndAt, zoneId, lineNumber, "end_at");

                if (!endAt.isAfter(startAt)) {
                    throw rowError(
                            "line " + lineNumber,
                            "Dòng " + lineNumber + ": Thời gian kết thúc (end_at) phải sau thời gian bắt đầu (start_at)."
                    );
                }

                String description = EventValidationSupport.trimToNull(readCell(row, columnIndex.get("description")));
                String location = EventValidationSupport.trimToNull(readCell(row, columnIndex.get("location")));

                items.add(new StoredImportPreviewItem(
                        itemIndex++,
                        null,
                        title.trim(),
                        description,
                        location,
                        startAt,
                        endAt,
                        eventTimezone
                ));
            }
        } catch (IOException | CsvValidationException ex) {
            throw new AppException(
                    ErrorCode.VALIDATION_ERROR,
                    "Không thể đọc tệp CSV: " + ex.getMessage()
            );
        }

        if (items.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Tệp CSV không có dòng dữ liệu sự kiện nào.");
        }
        return items;
    }

    private Instant resolveIcsEndInstant(
            VEvent vEvent,
            DtStart dtStart,
            Instant startAt,
            ZoneId zoneId,
            int eventNumber) {
        DtEnd dtEnd = vEvent.getEndDate();
        if (dtEnd != null && dtEnd.getDate() != null) {
            ZoneId endZone = dtEnd.getTimeZone() != null
                    ? ZoneId.of(resolveIcsZoneString(dtEnd.getTimeZone().getID(), zoneId.getId()))
                    : zoneId;
            return toUtcInstant(dtEnd.getDate(), endZone);
        }

        Duration durationProp = vEvent.getDuration();
        if (durationProp != null && durationProp.getDuration() != null) {
            TemporalAmount amount = durationProp.getDuration();
            return ZonedDateTime.ofInstant(startAt, zoneId).plus(amount).toInstant();
        }

        if (!(dtStart.getDate() instanceof DateTime)) {
            return ZonedDateTime.ofInstant(startAt, zoneId).plusDays(1).toInstant();
        }

        throw rowError("event " + eventNumber, "Sự kiện thứ " + eventNumber + " thiếu thời gian kết thúc (DTEND hoặc DURATION).");
    }

    private String resolveIcsTimezone(DtStart dtStart, String fallbackTimezone) {
        if (dtStart.getTimeZone() != null && StringUtils.hasText(dtStart.getTimeZone().getID())) {
            return resolveIcsZoneString(dtStart.getTimeZone().getID(), fallbackTimezone);
        }
        if (dtStart.isUtc()) {
            return "UTC";
        }
        return fallbackTimezone;
    }

    private String resolveIcsZoneString(String rawZoneId, String fallbackTimezone) {
        String trimmed = rawZoneId.trim();
        try {
            ZoneId.of(trimmed);
            return trimmed;
        } catch (DateTimeException ignored) {
            TimeZone tz = TimeZone.getTimeZone(trimmed);
            if (!"GMT".equals(tz.getID()) || "GMT".equalsIgnoreCase(trimmed) || "UTC".equalsIgnoreCase(trimmed)) {
                return tz.toZoneId().getId();
            }
            return fallbackTimezone;
        }
    }

    private Instant toUtcInstant(Date icalDate, ZoneId zoneId) {
        if (icalDate instanceof DateTime dateTime) {
            if (dateTime.isUtc() || dateTime.getTimeZone() != null) {
                return dateTime.toInstant();
            }
            String text = dateTime.toString();
            if (text.length() >= 15 && text.charAt(8) == 'T') {
                LocalDateTime localDateTime = LocalDateTime.parse(text.substring(0, 15), ICS_LOCAL_DATETIME_FORMAT);
                return localDateTime.atZone(zoneId).toInstant();
            }
            return dateTime.toInstant();
        }
        LocalDate localDate = LocalDate.parse(icalDate.toString().substring(0, 8), ICS_DATE_FORMAT);
        return localDate.atStartOfDay(zoneId).toInstant();
    }

    private Instant parseCsvTimestamp(String rawValue, ZoneId zoneId, int lineNumber, String columnName) {
        if (!StringUtils.hasText(rawValue)) {
            throw rowError("line " + lineNumber, "Dòng " + lineNumber + ": Thiếu giá trị cột " + columnName + ".");
        }
        String trimmed = rawValue.trim();
        try {
            return Instant.parse(trimmed);
        } catch (DateTimeException ignored) {
            // Try OffsetDateTime next
        }
        try {
            return OffsetDateTime.parse(trimmed).toInstant();
        } catch (DateTimeException ignored) {
            // Try local datetime formats in event timezone
        }
        for (DateTimeFormatter formatter : CSV_LOCAL_DATETIME_FORMATS) {
            try {
                LocalDateTime ldt = LocalDateTime.parse(trimmed, formatter);
                return ldt.atZone(zoneId).toInstant();
            } catch (DateTimeException ignored) {
                // Try next format
            }
        }
        throw rowError(
                "line " + lineNumber,
                "Dòng " + lineNumber + ": Giá trị cột " + columnName + " không đúng định dạng ngày giờ (" + trimmed + ")."
        );
    }

    private Map<String, Integer> mapCsvHeaders(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String cell = headers[i] != null ? headers[i] : "";
            if (i == 0 && cell.startsWith("\uFEFF")) {
                cell = cell.substring(1);
            }
            String normalized = cell.trim().toLowerCase(Locale.ROOT);
            if (!normalized.isEmpty()) {
                map.putIfAbsent(normalized, i);
            }
        }
        return map;
    }

    private void validateRequiredCsvColumns(Map<String, Integer> columnIndex) {
        List<String> missing = new ArrayList<>();
        if (!columnIndex.containsKey("title")) {
            missing.add("title");
        }
        if (!columnIndex.containsKey("start_at")) {
            missing.add("start_at");
        }
        if (!columnIndex.containsKey("end_at")) {
            missing.add("end_at");
        }
        if (!missing.isEmpty()) {
            String message = "Dòng 1: Thiếu cột bắt buộc trong CSV: " + String.join(", ", missing) + ".";
            throw rowError("line 1", message);
        }
    }

    private String readCell(String[] row, Integer index) {
        if (index == null || index < 0 || index >= row.length) {
            return null;
        }
        return row[index];
    }

    private boolean isBlankRow(String[] row) {
        for (String cell : row) {
            if (StringUtils.hasText(cell)) {
                return false;
            }
        }
        return true;
    }

    private String propertyValue(VEvent vEvent, String propertyName) {
        Property property = vEvent.getProperty(propertyName);
        return property != null ? property.getValue() : null;
    }

    private String truncateNullable(String value, int maxLen) {
        String trimmed = EventValidationSupport.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.length() > maxLen ? trimmed.substring(0, maxLen) : trimmed;
    }

    private AppException rowError(String field, String message) {
        return new AppException(
                ErrorCode.VALIDATION_ERROR,
                message,
                List.of(new FieldErrorDetail(field, message))
        );
    }
}
