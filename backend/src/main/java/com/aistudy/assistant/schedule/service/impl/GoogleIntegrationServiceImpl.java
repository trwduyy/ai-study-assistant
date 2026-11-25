package com.aistudy.assistant.schedule.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.security.TokenEncryptionService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.schedule.dto.GoogleAuthorizeResponse;
import com.aistudy.assistant.schedule.dto.GoogleConnectionResponse;
import com.aistudy.assistant.schedule.dto.GoogleSyncRequest;
import com.aistudy.assistant.schedule.dto.GoogleSyncResponse;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.entity.ExternalAccount;
import com.aistudy.assistant.schedule.integration.GoogleCalendarClient;
import com.aistudy.assistant.schedule.integration.GoogleCalendarEventItem;
import com.aistudy.assistant.schedule.integration.GoogleTokenResponse;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.repository.ExternalAccountRepository;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.aistudy.assistant.schedule.service.GoogleIntegrationService;
import com.aistudy.assistant.schedule.service.GoogleOAuthStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Read-only Google Calendar integration service (FR-16, D-06, BR-10).
 * All outbound Google OAuth/Calendar API HTTP calls execute strictly outside DB transactions.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoogleIntegrationServiceImpl implements GoogleIntegrationService {

    public static final String PROVIDER_GOOGLE = "GOOGLE";
    private static final String ORGANIZATION_EMAIL_DOMAIN = "@actvn.edu.vn";
    private static final Duration DEFAULT_SYNC_PAST_WINDOW = Duration.ofDays(7);
    private static final Duration DEFAULT_SYNC_FUTURE_WINDOW = Duration.ofDays(60);

    private final UserRepository userRepository;
    private final ExternalAccountRepository externalAccountRepository;
    private final EventRepository eventRepository;
    private final TokenEncryptionService tokenEncryptionService;
    private final GoogleOAuthStateService stateService;
    private final GoogleCalendarClient googleCalendarClient;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.google.allow-personal-accounts:false}")
    private boolean allowPersonalAccounts;

    @Override
    @Transactional(readOnly = true)
    public GoogleAuthorizeResponse authorize() {
        Long currentUserId = SecurityUtils.currentUserId();
        User user = userRepository.findById(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        enforceGooglePolicy(user.getEmail());

        String state = stateService.createState(currentUserId);
        String authorizeUrl = googleCalendarClient.buildAuthorizeUrl(state);
        return new GoogleAuthorizeResponse(authorizeUrl, List.of(GoogleCalendarClient.READONLY_SCOPE));
    }

    @Override
    public GoogleConnectionResponse handleCallback(String code, String state) {
        Long userId = stateService.verifyAndExtractUserId(state);
        verifyAuthenticatedUserMatchesIfPresent(userId);

        // Brief read check before calling Google OAuth outside transaction
        String userEmail = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            return userRepository.findById(userId)
                    .map(User::getEmail)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        });
        enforceGooglePolicy(userEmail);

        // External OAuth HTTP call strictly outside DB transaction
        GoogleTokenResponse tokenResponse = googleCalendarClient.exchangeAuthorizationCode(code);
        String encryptedRefreshToken = StringUtils.hasText(tokenResponse.refreshToken())
                ? tokenEncryptionService.encrypt(tokenResponse.refreshToken())
                : null;

        ExternalAccount savedAccount = transactionTemplate.execute(status -> {
            ExternalAccount account = externalAccountRepository.findByUserIdAndProvider(userId, PROVIDER_GOOGLE)
                    .orElseGet(() -> ExternalAccount.builder()
                            .user(userRepository.getReferenceById(userId))
                            .provider(PROVIDER_GOOGLE)
                            .build());

            if (encryptedRefreshToken != null) {
                account.setRefreshTokenEncrypted(encryptedRefreshToken);
            } else if (!StringUtils.hasText(account.getRefreshTokenEncrypted())) {
                throw new AppException(
                        ErrorCode.GOOGLE_API_ERROR,
                        "Google không cấp refresh token. Vui lòng thu hồi quyền cũ và thử kết nối lại."
                );
            }

            account.setScopes(StringUtils.hasText(tokenResponse.scope())
                    ? tokenResponse.scope()
                    : GoogleCalendarClient.READONLY_SCOPE);
            account.setConnectedAt(Instant.now());
            return externalAccountRepository.save(account);
        });

        log.info("Connected Google Calendar (read-only) for userId={}", userId);
        return new GoogleConnectionResponse(
                PROVIDER_GOOGLE,
                true,
                savedAccount.getConnectedAt(),
                savedAccount.getLastSyncedAt()
        );
    }

    @Override
    public GoogleSyncResponse syncCalendar(GoogleSyncRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        Instant now = Instant.now();
        Instant from = request != null && request.getFrom() != null
                ? request.getFrom()
                : now.minus(DEFAULT_SYNC_PAST_WINDOW);
        Instant to = request != null && request.getTo() != null
                ? request.getTo()
                : now.plus(DEFAULT_SYNC_FUTURE_WINDOW);
        EventValidationSupport.validateTimeWindow(from, to);

        // 1. Read encrypted refresh token in short read-only transaction
        String encryptedRefreshToken = transactionTemplate.execute(status -> {
            status.setRollbackOnly();
            ExternalAccount account = externalAccountRepository.findByUserIdAndProvider(currentUserId, PROVIDER_GOOGLE)
                    .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));
            return account.getRefreshTokenEncrypted();
        });

        // 2. Decrypt & call Google OAuth + Calendar API strictly outside DB transaction
        String refreshToken = tokenEncryptionService.decrypt(encryptedRefreshToken);
        String accessToken = googleCalendarClient.refreshAccessToken(refreshToken);
        List<GoogleCalendarEventItem> fetchedItems = googleCalendarClient.fetchPrimaryEvents(accessToken, from, to);

        // 3. Upsert fetched events atomically inside DB transaction
        GoogleSyncResponse response = transactionTemplate.execute(status -> {
            ExternalAccount account = externalAccountRepository.findByUserIdAndProvider(currentUserId, PROVIDER_GOOGLE)
                    .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));

            List<String> externalIds = fetchedItems.stream()
                    .map(GoogleCalendarEventItem::externalId)
                    .filter(StringUtils::hasText)
                    .distinct()
                    .toList();

            Map<String, Event> existingByExternalId = externalIds.isEmpty()
                    ? Map.of()
                    : eventRepository.findByOwnerIdAndSourceAndExternalIdIn(currentUserId, EventSource.GOOGLE, externalIds)
                            .stream()
                            .collect(Collectors.toMap(Event::getExternalId, Function.identity(), (first, ignored) -> first));

            User owner = userRepository.getReferenceById(currentUserId);
            List<Event> toSave = new ArrayList<>();
            int createdCount = 0;
            int updatedCount = 0;
            int cancelledCount = 0;

            for (GoogleCalendarEventItem item : fetchedItems) {
                Event existing = existingByExternalId.get(item.externalId());
                if ("cancelled".equalsIgnoreCase(item.status())) {
                    if (existing != null && existing.getStatus() != EventStatus.CANCELLED) {
                        existing.setStatus(EventStatus.CANCELLED);
                        toSave.add(existing);
                        cancelledCount++;
                    }
                    continue;
                }

                if (existing == null) {
                    Event created = Event.builder()
                            .owner(owner)
                            .classroom(null)
                            .type(EventType.PERSONAL)
                            .title(item.title())
                            .description(item.description())
                            .startAt(item.startAt())
                            .endAt(item.endAt())
                            .timezone(item.timezone())
                            .status(EventStatus.UPCOMING)
                            .meetingUrl(item.meetingUrl())
                            .source(EventSource.GOOGLE)
                            .externalId(item.externalId())
                            .build();
                    toSave.add(created);
                    createdCount++;
                } else {
                    existing.setTitle(item.title());
                    existing.setDescription(item.description());
                    existing.setStartAt(item.startAt());
                    existing.setEndAt(item.endAt());
                    existing.setTimezone(item.timezone());
                    existing.setMeetingUrl(item.meetingUrl());
                    existing.setStatus(EventStatus.UPCOMING);
                    toSave.add(existing);
                    updatedCount++;
                }
            }

            if (!toSave.isEmpty()) {
                eventRepository.saveAll(toSave);
            }

            Instant syncedAt = Instant.now();
            account.setLastSyncedAt(syncedAt);
            externalAccountRepository.save(account);

            return new GoogleSyncResponse(
                    PROVIDER_GOOGLE,
                    createdCount + updatedCount + cancelledCount,
                    createdCount,
                    updatedCount,
                    cancelledCount,
                    syncedAt
            );
        });

        log.info("Synced Google Calendar for userId={} created={} updated={} cancelled={}",
                currentUserId, response.createdCount(), response.updatedCount(), response.cancelledCount());
        return response;
    }

    @Override
    @Transactional
    public void disconnectGoogle() {
        Long currentUserId = SecurityUtils.currentUserId();
        ExternalAccount account = externalAccountRepository.findByUserIdAndProvider(currentUserId, PROVIDER_GOOGLE)
                .orElseThrow(() -> new AppException(ErrorCode.EXTERNAL_ACCOUNT_NOT_FOUND));

        eventRepository.deleteByOwnerIdAndSource(currentUserId, EventSource.GOOGLE);
        externalAccountRepository.delete(account);
        log.info("Disconnected Google Calendar and removed synced Google events for userId={}", currentUserId);
    }

    private void enforceGooglePolicy(String email) {
        if (allowPersonalAccounts) {
            return;
        }
        String normalized = email != null ? email.trim().toLowerCase(Locale.ROOT) : "";
        if (!normalized.endsWith(ORGANIZATION_EMAIL_DOMAIN)) {
            throw new AppException(
                    ErrorCode.FORBIDDEN,
                    "Tài khoản cá nhân chưa được phép đồng bộ Google Calendar. Vui lòng sử dụng email tổ chức ("
                            + ORGANIZATION_EMAIL_DOMAIN + ") hoặc liên hệ quản trị viên."
            );
        }
    }

    private void verifyAuthenticatedUserMatchesIfPresent(Long stateUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            if (!principal.getId().equals(stateUserId)) {
                throw new AppException(ErrorCode.FORBIDDEN, "Trạng thái OAuth không khớp với người dùng đang đăng nhập.");
            }
        }
    }
}
