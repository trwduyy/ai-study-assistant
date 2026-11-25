package com.aistudy.assistant.schedule.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.common.security.TokenEncryptionService;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.UserPrincipal;
import com.aistudy.assistant.schedule.dto.GoogleAuthorizeResponse;
import com.aistudy.assistant.schedule.dto.GoogleSyncRequest;
import com.aistudy.assistant.schedule.dto.GoogleSyncResponse;
import com.aistudy.assistant.schedule.entity.Event;
import com.aistudy.assistant.schedule.entity.EventSource;
import com.aistudy.assistant.schedule.entity.EventStatus;
import com.aistudy.assistant.schedule.entity.EventType;
import com.aistudy.assistant.schedule.entity.ExternalAccount;
import com.aistudy.assistant.schedule.integration.GoogleCalendarClient;
import com.aistudy.assistant.schedule.integration.GoogleCalendarEventItem;
import com.aistudy.assistant.schedule.repository.EventRepository;
import com.aistudy.assistant.schedule.repository.ExternalAccountRepository;
import com.aistudy.assistant.schedule.service.impl.GoogleIntegrationServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleIntegrationServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ExternalAccountRepository externalAccountRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private GoogleCalendarClient googleCalendarClient;
    @Mock
    private TransactionTemplate transactionTemplate;

    private TokenEncryptionService tokenEncryptionService;
    private GoogleOAuthStateService stateService;
    private GoogleIntegrationServiceImpl googleIntegrationService;

    private User orgUser;
    private User personalUser;

    @BeforeEach
    void setUp() {
        String base64Key = Base64.getEncoder().encodeToString(
                "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)
        );
        tokenEncryptionService = new TokenEncryptionService(base64Key);
        stateService = new GoogleOAuthStateService(base64Key);

        googleIntegrationService = new GoogleIntegrationServiceImpl(
                userRepository,
                externalAccountRepository,
                eventRepository,
                tokenEncryptionService,
                stateService,
                googleCalendarClient,
                transactionTemplate
        );
        ReflectionTestUtils.setField(googleIntegrationService, "allowPersonalAccounts", false);

        orgUser = User.builder()
                .id(11L)
                .email("sv01@actvn.edu.vn")
                .fullName("Nguyễn Văn A")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        personalUser = User.builder()
                .id(12L)
                .email("sv02@gmail.com")
                .fullName("Trần Văn B")
                .passwordHash("hash")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .build();

        authenticateAs(orgUser);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("authorize blocks personal email when allowPersonalAccounts=false with 403 FORBIDDEN")
    void authorize_PersonalEmailDisallowed_ThrowsForbidden() {
        authenticateAs(personalUser);
        when(userRepository.findById(12L)).thenReturn(Optional.of(personalUser));

        AppException ex = assertThrows(AppException.class, () -> googleIntegrationService.authorize());
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("authorize returns Google OAuth URL with read-only scope for @actvn.edu.vn user")
    void authorize_OrganizationEmail_ReturnsReadOnlyAuthorizeUrl() {
        when(userRepository.findById(11L)).thenReturn(Optional.of(orgUser));
        when(googleCalendarClient.buildAuthorizeUrl(any())).thenAnswer(inv ->
                "https://accounts.google.com/o/oauth2/v2/auth?state=" + inv.getArgument(0)
        );

        GoogleAuthorizeResponse response = googleIntegrationService.authorize();

        assertEquals(List.of(GoogleCalendarClient.READONLY_SCOPE), response.scopes());
        assertTrue(response.authorizeUrl().startsWith("https://accounts.google.com/o/oauth2/v2/auth?state="));
    }

    @Test
    @DisplayName("syncCalendar twice with same externalId upserts without creating duplicates")
    void syncCalendar_TwiceWithSameExternalId_UpsertsWithoutDuplicate() {
        when(transactionTemplate.execute(any())).thenAnswer(inv -> {
            TransactionCallback<?> callback = inv.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        String encryptedToken = tokenEncryptionService.encrypt("1//0gGoogleRefreshToken");
        ExternalAccount account = ExternalAccount.builder()
                .id(1L)
                .user(orgUser)
                .provider("GOOGLE")
                .scopes(GoogleCalendarClient.READONLY_SCOPE)
                .refreshTokenEncrypted(encryptedToken)
                .connectedAt(Instant.now())
                .build();

        when(externalAccountRepository.findByUserIdAndProvider(11L, "GOOGLE")).thenReturn(Optional.of(account));
        when(googleCalendarClient.refreshAccessToken("1//0gGoogleRefreshToken")).thenReturn("ya29.access");
        when(userRepository.getReferenceById(11L)).thenReturn(orgUser);

        GoogleCalendarEventItem initialGoogleEvent = new GoogleCalendarEventItem(
                "google-evt-abc",
                "confirmed",
                "Lịch Google ban đầu",
                "Mô tả 1",
                Instant.parse("2026-11-28T01:00:00Z"),
                Instant.parse("2026-11-28T02:00:00Z"),
                "Asia/Ho_Chi_Minh",
                "https://meet.google.com/aaa-bbbb-ccc"
        );
        GoogleCalendarEventItem updatedGoogleEvent = new GoogleCalendarEventItem(
                "google-evt-abc",
                "confirmed",
                "Lịch Google đã cập nhật",
                "Mô tả 2",
                Instant.parse("2026-11-28T02:00:00Z"),
                Instant.parse("2026-11-28T03:00:00Z"),
                "Asia/Ho_Chi_Minh",
                "https://meet.google.com/aaa-bbbb-ccc"
        );

        when(googleCalendarClient.fetchPrimaryEvents(any(), any(), any()))
                .thenReturn(List.of(initialGoogleEvent))
                .thenReturn(List.of(updatedGoogleEvent));

        Event persistedEvent = Event.builder()
                .id(9001L)
                .owner(orgUser)
                .type(EventType.PERSONAL)
                .source(EventSource.GOOGLE)
                .externalId("google-evt-abc")
                .title("Lịch Google ban đầu")
                .startAt(Instant.parse("2026-11-28T01:00:00Z"))
                .endAt(Instant.parse("2026-11-28T02:00:00Z"))
                .timezone("Asia/Ho_Chi_Minh")
                .status(EventStatus.UPCOMING)
                .build();

        when(eventRepository.findByOwnerIdAndSourceAndExternalIdIn(11L, EventSource.GOOGLE, List.of("google-evt-abc")))
                .thenReturn(List.of())
                .thenReturn(List.of(persistedEvent));
        when(eventRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
        when(externalAccountRepository.save(any(ExternalAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        GoogleSyncRequest syncReq = new GoogleSyncRequest(
                Instant.parse("2026-11-25T00:00:00Z"),
                Instant.parse("2026-12-25T00:00:00Z")
        );

        GoogleSyncResponse firstSync = googleIntegrationService.syncCalendar(syncReq);
        GoogleSyncResponse secondSync = googleIntegrationService.syncCalendar(syncReq);

        assertEquals(1, firstSync.createdCount());
        assertEquals(0, firstSync.updatedCount());
        assertEquals(0, secondSync.createdCount());
        assertEquals(1, secondSync.updatedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Event>> savedEventsCaptor = ArgumentCaptor.forClass(List.class);
        verify(eventRepository, times(2)).saveAll(savedEventsCaptor.capture());

        List<Event> secondBatch = savedEventsCaptor.getAllValues().get(1);
        assertEquals(1, secondBatch.size());
        assertSame(persistedEvent, secondBatch.get(0), "Second sync must reuse existing Event record instead of inserting a duplicate");
        assertEquals("Lịch Google đã cập nhật", persistedEvent.getTitle());
        assertEquals(Instant.parse("2026-11-28T02:00:00Z"), persistedEvent.getStartAt());
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }
}
