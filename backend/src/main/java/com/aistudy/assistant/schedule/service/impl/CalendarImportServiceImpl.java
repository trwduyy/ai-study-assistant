package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.classroom.repository.ClassMemberRepository;
import com.aistudy.assistant.classroom.repository.ClassMemberRepository.ActiveClassRoleProjection;
import com.aistudy.assistant.common.dto.FieldErrorDetail;
import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.schedule.dto.CalendarImportItemDto;
import com.aistudy.assistant.schedule.dto.CalendarImportPreviewResponse;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportRequest;
import com.aistudy.assistant.schedule.dto.ConfirmCalendarImportResponse;
import com.aistudy.assistant.schedule.dto.ConflictingEventSummary;
import com.aistudy.assistant.schedule.dto.StoredImportPreviewItem;
import com.aistudy.assistant.schedule.entity.CalendarImport;
import com.aistudy.assistant.schedule.entity.CalendarImportSourceType;
import com.aistudy.assistant.schedule.entity.CalendarImportStatus;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.repository.CalendarImportRepository;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.service.CalendarImportParser;
import com.aistudy.assistant.schedule.service.CalendarImportService;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Business service for importing {@code .ics} and {@code .csv} schedules with preview,
 * database-backed overlap detection, and user confirmation (FR-15).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalendarImportServiceImpl implements CalendarImportService {

    private static final long MAX_IMPORT_FILE_BYTES = 5L * 1024L * 1024L;
    private static final String TEMPLATE_CSV_CONTENT = """
            title,description,start_at,end_at,timezone,location
            "Ôn tập Mạng máy tính","Chương 3: Tầng giao vận TCP/UDP",2026-11-26T00:00:00Z,2026-11-26T02:00:00Z,Asia/Ho_Chi_Minh,"Phòng 402-TA2"
            """;

    private final CalendarImportRepository calendarImportRepository;
    private final EventRepository eventRepository;
    private final ClassMemberRepository classMemberRepository;
    private final UserRepository userRepository;
    private final CalendarImportParser calendarImportParser;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public byte[] getImportTemplateCsv() {
        return TEMPLATE_CSV_CONTENT.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public CalendarImportPreviewResponse previewImport(MultipartFile file, String defaultTimezone) {
        Long currentUserId = SecurityUtils.currentUserId();
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Vui lòng chọn tệp .ics hoặc .csv để nhập lịch.");
        }
        if (file.getSize() > MAX_IMPORT_FILE_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE, "Dung lượng tệp nhập lịch vượt quá giới hạn 5MB.");
        }

        String sanitizedFileName = sanitizeFileName(file.getOriginalFilename());
        CalendarImportSourceType sourceType = resolveSourceType(sanitizedFileName);
        String normalizedTimezone = EventValidationSupport.normalizeAndValidateTimezone(defaultTimezone);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new AppException(ErrorCode.BAD_REQUEST, "Không thể đọc nội dung tệp tải lên.");
        }

        // Parse outside DB transaction
        List<StoredImportPreviewItem> parsedItems = sourceType == CalendarImportSourceType.ICS
                ? calendarImportParser.parseIcs(content, normalizedTimezone)
                : calendarImportParser.parseCsv(content, normalizedTimezone);

        String previewJson = serializePreviewItems(parsedItems);

        return transactionTemplate.execute(status -> {
            Instant rangeFrom = parsedItems.stream()
                    .map(StoredImportPreviewItem::startAt)
                    .min(Instant::compareTo)
                    .orElseThrow();
            Instant rangeTo = parsedItems.stream()
                    .map(StoredImportPreviewItem::endAt)
                    .max(Instant::compareTo)
                    .orElseThrow();

            List<Long> activeClassIds = classMemberRepository.findActiveClassRolesByUserId(currentUserId).stream()
                    .map(ActiveClassRoleProjection::getClassId)
                    .distinct()
                    .toList();

            List<Event> existingEvents = activeClassIds.isEmpty()
                    ? eventRepository.findOverlappingOwnerEvents(currentUserId, rangeFrom, rangeTo, null)
                    : eventRepository.findOverlappingCalendarEvents(currentUserId, activeClassIds, rangeFrom, rangeTo, null);

            List<CalendarImportItemDto> previewItems = new ArrayList<>(parsedItems.size());
            int conflictCount = 0;
            for (StoredImportPreviewItem item : parsedItems) {
                List<ConflictingEventSummary> conflicts = existingEvents.stream()
                        .filter(existing -> existing.getStartAt().isBefore(item.endAt())
                                && item.startAt().isBefore(existing.getEndAt()))
                        .map(existing -> new ConflictingEventSummary(
                                existing.getId(),
                                existing.getTitle(),
                                existing.getType(),
                                existing.getClassroom() != null ? existing.getClassroom().getId() : null,
                                existing.getClassroom() != null ? existing.getClassroom().getName() : null,
                                existing.getStartAt(),
                                existing.getEndAt(),
                                existing.getSource()
                        ))
                        .toList();
                boolean hasConflict = !conflicts.isEmpty();
                if (hasConflict) {
                    conflictCount++;
                }
                previewItems.add(new CalendarImportItemDto(
                        item.itemIndex(),
                        item.externalId(),
                        item.title(),
                        item.description(),
                        item.location(),
                        item.startAt(),
                        item.endAt(),
                        item.timezone(),
                        hasConflict,
                        conflicts
                ));
            }

            User user = userRepository.getReferenceById(currentUserId);
            CalendarImport savedImport = calendarImportRepository.save(CalendarImport.builder()
                    .user(user)
                    .sourceType(sourceType)
                    .fileName(sanitizedFileName)
                    .timezone(normalizedTimezone)
                    .status(CalendarImportStatus.PREVIEW)
                    .previewJson(previewJson)
                    .build());

            log.info("Created calendar import preview id={} source={} total={} conflicts={} userId={}",
                    savedImport.getId(), sourceType, previewItems.size(), conflictCount, currentUserId);

            return new CalendarImportPreviewResponse(
                    savedImport.getId(),
                    savedImport.getSourceType(),
                    savedImport.getFileName(),
                    savedImport.getTimezone(),
                    savedImport.getStatus(),
                    previewItems.size(),
                    conflictCount,
                    previewItems
            );
        });
    }

    @Override
    @Transactional
    public ConfirmCalendarImportResponse confirmImport(Long importId, ConfirmCalendarImportRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        CalendarImport calendarImport = calendarImportRepository.findById(importId)
                .orElseThrow(() -> new AppException(ErrorCode.CALENDAR_IMPORT_NOT_FOUND));

        if (!calendarImport.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không có quyền xác nhận phiên nhập lịch này.");
        }
        if (calendarImport.getStatus() != CalendarImportStatus.PREVIEW) {
            throw new AppException(ErrorCode.UNPROCESSABLE_ENTITY, "Phiên nhập lịch này đã được xác nhận hoặc hủy trước đó.");
        }

        List<StoredImportPreviewItem> parsedItems = deserializePreviewItems(calendarImport.getPreviewJson());
        Set<Integer> acceptedIndices = normalizeAcceptedIndices(request.getAcceptedItemIndices(), parsedItems.size());

        EventSource eventSource = calendarImport.getSourceType() == CalendarImportSourceType.ICS
                ? EventSource.ICS
                : EventSource.CSV;

        List<StoredImportPreviewItem> selectedItems = acceptedIndices.stream()
                .map(parsedItems::get)
                .toList();

        List<String> externalIds = selectedItems.stream()
                .map(StoredImportPreviewItem::externalId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();

        Map<String, Event> existingByExternalId = externalIds.isEmpty()
                ? Map.of()
                : eventRepository.findByOwnerIdAndSourceAndExternalIdIn(currentUserId, eventSource, externalIds).stream()
                        .collect(Collectors.toMap(Event::getExternalId, Function.identity(), (first, ignored) -> first));

        User owner = userRepository.getReferenceById(currentUserId);
        Map<String, Event> dedupedNewByExtId = new LinkedHashMap<>();
        List<Event> eventsToSave = new ArrayList<>();

        for (StoredImportPreviewItem item : selectedItems) {
            String combinedDescription = combineDescriptionAndLocation(item.description(), item.location());
            String extId = EventValidationSupport.trimToNull(item.externalId());
            Event target = extId != null
                    ? existingByExternalId.getOrDefault(extId, dedupedNewByExtId.get(extId))
                    : null;

            if (target == null) {
                target = Event.builder()
                        .owner(owner)
                        .classroom(null)
                        .calendarImport(calendarImport)
                        .type(EventType.PERSONAL)
                        .source(eventSource)
                        .externalId(extId)
                        .status(EventStatus.UPCOMING)
                        .build();
                if (extId != null) {
                    dedupedNewByExtId.put(extId, target);
                }
                eventsToSave.add(target);
            } else {
                target.setCalendarImport(calendarImport);
                target.setStatus(EventStatus.UPCOMING);
                if (!eventsToSave.contains(target)) {
                    eventsToSave.add(target);
                }
            }

            target.setTitle(item.title());
            target.setDescription(combinedDescription);
            target.setStartAt(item.startAt());
            target.setEndAt(item.endAt());
            target.setTimezone(item.timezone());
        }

        List<Event> savedEvents = eventsToSave.isEmpty() ? List.of() : eventRepository.saveAll(eventsToSave);
        calendarImport.setStatus(CalendarImportStatus.CONFIRMED);
        calendarImportRepository.save(calendarImport);

        int importedCount = savedEvents.size();
        int skippedCount = Math.max(0, parsedItems.size() - acceptedIndices.size());
        List<Long> createdIds = savedEvents.stream().map(Event::getId).toList();

        log.info("Confirmed calendar import id={} imported={} skipped={} userId={}",
                importId, importedCount, skippedCount, currentUserId);

        return new ConfirmCalendarImportResponse(
                calendarImport.getId(),
                calendarImport.getStatus(),
                importedCount,
                skippedCount,
                createdIds
        );
    }

    private Set<Integer> normalizeAcceptedIndices(List<Integer> rawIndices, int totalSize) {
        Set<Integer> normalized = new LinkedHashSet<>();
        if (rawIndices == null) {
            return normalized;
        }
        for (Integer idx : rawIndices) {
            if (idx == null || idx < 0 || idx >= totalSize) {
                throw new AppException(
                        ErrorCode.VALIDATION_ERROR,
                        "Chỉ mục sự kiện được chọn không hợp lệ: " + idx,
                        List.of(new FieldErrorDetail("acceptedItemIndices", "Chỉ mục phải nằm trong khoảng 0.." + Math.max(0, totalSize - 1)))
                );
            }
            normalized.add(idx);
        }
        return normalized;
    }

    private String sanitizeFileName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new AppException(ErrorCode.INVALID_FILE_NAME);
        }
        String baseName = originalFilename.replace('\\', '/');
        int slashIdx = baseName.lastIndexOf('/');
        if (slashIdx >= 0) {
            baseName = baseName.substring(slashIdx + 1);
        }
        baseName = baseName.trim();
        if (baseName.isEmpty() || baseName.contains("..")) {
            throw new AppException(ErrorCode.INVALID_FILE_NAME);
        }
        return baseName.length() > 255 ? baseName.substring(0, 255) : baseName;
    }

    private CalendarImportSourceType resolveSourceType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".ics")) {
            return CalendarImportSourceType.ICS;
        }
        if (lower.endsWith(".csv")) {
            return CalendarImportSourceType.CSV;
        }
        throw new AppException(ErrorCode.FILE_TYPE_NOT_ALLOWED, "Chỉ hỗ trợ nhập lịch từ tệp .ics hoặc .csv.");
    }

    private String serializePreviewItems(List<StoredImportPreviewItem> items) {
        try {
            return objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể lưu dữ liệu xem trước nhập lịch.");
        }
    }

    private List<StoredImportPreviewItem> deserializePreviewItems(String previewJson) {
        if (!StringUtils.hasText(previewJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(previewJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            throw new AppException(ErrorCode.INTERNAL_ERROR, "Không thể đọc dữ liệu xem trước nhập lịch.");
        }
    }

    private String combineDescriptionAndLocation(String description, String location) {
        String cleanDesc = EventValidationSupport.trimToNull(description);
        String cleanLoc = EventValidationSupport.trimToNull(location);
        if (cleanLoc == null) {
            return cleanDesc;
        }
        if (cleanDesc == null) {
            return "Địa điểm: " + cleanLoc;
        }
        if (Objects.equals(cleanDesc, cleanLoc)) {
            return cleanDesc;
        }
        return cleanDesc + "\nĐịa điểm: " + cleanLoc;
    }
}
