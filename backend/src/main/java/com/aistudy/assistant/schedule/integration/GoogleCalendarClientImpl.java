package com.aistudy.assistant.schedule.integration;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.schedule.service.EventValidationSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * RestClient-based read-only Google OAuth2 and Google Calendar API v3 integration.
 * Strictly performs only OAuth token exchange/refresh and read-only calendar event queries.
 */
@Slf4j
@Service
public class GoogleCalendarClientImpl implements GoogleCalendarClient {

    private static final String GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_CALENDAR_EVENTS_ENDPOINT = "https://www.googleapis.com/calendar/v3/calendars/primary/events";
    private static final int MAX_SYNC_PAGES = 10;

    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Autowired
    public GoogleCalendarClientImpl(
            @Value("${app.google.client-id:}") String clientId,
            @Value("${app.google.client-secret:}") String clientSecret,
            @Value("${app.google.redirect-uri:http://localhost:8080/api/v1/integrations/google/callback}") String redirectUri,
            ObjectMapper objectMapper) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.redirectUri = redirectUri != null ? redirectUri.trim() : "";
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    GoogleCalendarClientImpl(
            String clientId,
            String clientSecret,
            String redirectUri,
            RestClient restClient,
            ObjectMapper objectMapper) {
        this.clientId = clientId != null ? clientId.trim() : "";
        this.clientSecret = clientSecret != null ? clientSecret.trim() : "";
        this.redirectUri = redirectUri != null ? redirectUri.trim() : "";
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String buildAuthorizeUrl(String state) {
        ensureOAuthConfigured();
        return UriComponentsBuilder.fromHttpUrl(GOOGLE_AUTH_ENDPOINT)
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", READONLY_SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("include_granted_scopes", "true")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    @Override
    public GoogleTokenResponse exchangeAuthorizationCode(String code) {
        ensureOAuthConfigured();
        if (!StringUtils.hasText(code)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Thiếu mã ủy quyền (code) từ Google.");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("code", code.trim());
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("redirect_uri", redirectUri);
        formData.add("grant_type", "authorization_code");

        JsonNode root = postFormForJson(GOOGLE_TOKEN_ENDPOINT, formData);
        String accessToken = textOrNull(root, "access_token");
        String refreshToken = textOrNull(root, "refresh_token");
        String scope = textOrNull(root, "scope");
        Long expiresIn = root.hasNonNull("expires_in") ? root.get("expires_in").asLong() : null;

        if (!StringUtils.hasText(accessToken)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Google không trả về access token hợp lệ.");
        }
        return new GoogleTokenResponse(accessToken, refreshToken, scope, expiresIn);
    }

    @Override
    public String refreshAccessToken(String refreshToken) {
        ensureOAuthConfigured();
        if (!StringUtils.hasText(refreshToken)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Refresh token Google không hợp lệ.");
        }

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("refresh_token", refreshToken);
        formData.add("client_id", clientId);
        formData.add("client_secret", clientSecret);
        formData.add("grant_type", "refresh_token");

        JsonNode root = postFormForJson(GOOGLE_TOKEN_ENDPOINT, formData);
        String accessToken = textOrNull(root, "access_token");
        if (!StringUtils.hasText(accessToken)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Không thể làm mới access token từ Google.");
        }
        return accessToken;
    }

    @Override
    public List<GoogleCalendarEventItem> fetchPrimaryEvents(String accessToken, Instant timeMin, Instant timeMax) {
        if (!StringUtils.hasText(accessToken)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Thiếu access token Google Calendar.");
        }

        List<GoogleCalendarEventItem> results = new ArrayList<>();
        String pageToken = null;
        int pageCount = 0;

        do {
            UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(GOOGLE_CALENDAR_EVENTS_ENDPOINT)
                    .queryParam("singleEvents", "true")
                    .queryParam("orderBy", "startTime")
                    .queryParam("maxResults", "250")
                    .queryParam("timeMin", timeMin.toString())
                    .queryParam("timeMax", timeMax.toString());
            if (StringUtils.hasText(pageToken)) {
                uriBuilder.queryParam("pageToken", pageToken);
            }

            JsonNode root = getJsonWithBearer(uriBuilder.build(true).toUriString(), accessToken);
            String calendarDefaultTz = normalizeZoneOrDefault(textOrNull(root, "timeZone"));

            JsonNode itemsNode = root.get("items");
            if (itemsNode != null && itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    GoogleCalendarEventItem parsed = parseCalendarItem(item, calendarDefaultTz);
                    if (parsed != null) {
                        results.add(parsed);
                    }
                }
            }

            pageToken = textOrNull(root, "nextPageToken");
            pageCount++;
        } while (StringUtils.hasText(pageToken) && pageCount < MAX_SYNC_PAGES);

        return results;
    }

    private GoogleCalendarEventItem parseCalendarItem(JsonNode item, String calendarDefaultTz) {
        String id = textOrNull(item, "id");
        if (!StringUtils.hasText(id)) {
            return null;
        }
        String status = textOrNull(item, "status");
        String title = textOrNull(item, "summary");
        if (!StringUtils.hasText(title)) {
            title = "(Sự kiện Google Calendar)";
        }
        if (title.length() > 255) {
            title = title.substring(0, 255);
        }

        if ("cancelled".equalsIgnoreCase(status)) {
            return new GoogleCalendarEventItem(id, "cancelled", title, null, null, null, calendarDefaultTz, null);
        }

        JsonNode startNode = item.get("start");
        JsonNode endNode = item.get("end");
        if (startNode == null || endNode == null) {
            return null;
        }

        String timezone = normalizeZoneOrDefault(textOrNull(startNode, "timeZone"), calendarDefaultTz);
        ZoneId zoneId = ZoneId.of(timezone);
        Instant startAt = parseGoogleDateOrDateTime(startNode, zoneId);
        Instant endAt = parseGoogleDateOrDateTime(endNode, zoneId);
        if (startAt == null || endAt == null || !endAt.isAfter(startAt)) {
            return null;
        }

        String description = EventValidationSupport.trimToNull(textOrNull(item, "description"));
        String meetingUrl = sanitizeMeetingUrl(textOrNull(item, "hangoutLink"));

        return new GoogleCalendarEventItem(
                id.length() > 255 ? id.substring(0, 255) : id,
                status != null ? status : "confirmed",
                title,
                description,
                startAt,
                endAt,
                timezone,
                meetingUrl
        );
    }

    private Instant parseGoogleDateOrDateTime(JsonNode node, ZoneId zoneId) {
        String dateTimeStr = textOrNull(node, "dateTime");
        if (StringUtils.hasText(dateTimeStr)) {
            try {
                return OffsetDateTime.parse(dateTimeStr).toInstant();
            } catch (DateTimeException ex) {
                return Instant.parse(dateTimeStr);
            }
        }
        String dateStr = textOrNull(node, "date");
        if (StringUtils.hasText(dateStr)) {
            return LocalDate.parse(dateStr).atStartOfDay(zoneId).toInstant();
        }
        return null;
    }

    private JsonNode postFormForJson(String url, MultiValueMap<String, String> formData) {
        try {
            String raw = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(String.class);
            return parseJson(raw);
        } catch (RestClientResponseException ex) {
            log.warn("Google OAuth HTTP error status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Google OAuth từ chối hoặc gặp lỗi kết nối.");
        } catch (ResourceAccessException ex) {
            throw mapNetworkException(ex);
        }
    }

    private JsonNode getJsonWithBearer(String url, String accessToken) {
        try {
            String raw = restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);
            return parseJson(raw);
        } catch (RestClientResponseException ex) {
            log.warn("Google Calendar API error status={} body={}", ex.getStatusCode().value(), ex.getResponseBodyAsString());
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Không thể đọc dữ liệu từ Google Calendar API.");
        } catch (ResourceAccessException ex) {
            throw mapNetworkException(ex);
        }
    }

    private JsonNode parseJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Phản hồi từ Google API rỗng.");
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Không thể phân tích phản hồi JSON từ Google API.");
        }
    }

    private AppException mapNetworkException(ResourceAccessException ex) {
        Throwable cause = ex.getCause();
        if (cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException) {
            return new AppException(ErrorCode.EXTERNAL_API_TIMEOUT, "Kết nối Google API bị quá thời gian chờ.");
        }
        return new AppException(ErrorCode.GOOGLE_API_ERROR, "Không thể kết nối tới Google API.");
    }

    private void ensureOAuthConfigured() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret) || !StringUtils.hasText(redirectUri)) {
            throw new AppException(ErrorCode.GOOGLE_API_ERROR, "Google Calendar OAuth chưa được cấu hình trên máy chủ.");
        }
    }

    private String normalizeZoneOrDefault(String rawZone) {
        return normalizeZoneOrDefault(rawZone, EventValidationSupport.DEFAULT_TIMEZONE);
    }

    private String normalizeZoneOrDefault(String rawZone, String fallback) {
        if (!StringUtils.hasText(rawZone)) {
            return fallback;
        }
        try {
            ZoneId.of(rawZone.trim());
            return rawZone.trim();
        } catch (DateTimeException ex) {
            return fallback;
        }
    }

    private String sanitizeMeetingUrl(String rawUrl) {
        try {
            return EventValidationSupport.normalizeAndValidateMeetingUrl(rawUrl);
        } catch (AppException ignored) {
            return null;
        }
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || !node.hasNonNull(fieldName)) {
            return null;
        }
        String value = node.get(fieldName).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
