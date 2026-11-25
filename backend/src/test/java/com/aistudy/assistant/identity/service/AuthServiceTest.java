package com.aistudy.assistant.identity.service;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.dto.AuthResponse;
import com.aistudy.assistant.identity.dto.LoginRequest;
import com.aistudy.assistant.identity.dto.RefreshTokenRequest;
import com.aistudy.assistant.identity.dto.RegisterRequest;
import com.aistudy.assistant.identity.dto.TokenRefreshResponse;
import com.aistudy.assistant.identity.dto.UserProfileResponse;
import com.aistudy.assistant.identity.entity.RefreshToken;
import com.aistudy.assistant.identity.entity.User;
import com.aistudy.assistant.identity.entity.UserRole;
import com.aistudy.assistant.identity.entity.UserStatus;
import com.aistudy.assistant.identity.mapper.UserMapper;
import com.aistudy.assistant.identity.repository.RefreshTokenRepository;
import com.aistudy.assistant.identity.repository.UserRepository;
import com.aistudy.assistant.identity.security.JwtTokenProvider;
import com.aistudy.assistant.identity.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private AuthServiceImpl authService;

    private User activeStudent;

    @BeforeEach
    void setUp() {
        activeStudent = User.builder()
                .id(12L)
                .email("sv01@actvn.edu.vn")
                .passwordHash("$2a$10$encodedHash")
                .fullName("Nguyễn Văn A")
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .version(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Should register new student account with normalized email and STUDENT role")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .email("  SV01@ACTVN.EDU.VN ")
                .password("StrongPassword@123")
                .fullName("  Nguyễn Văn A ")
                .build();

        when(userRepository.existsByEmailIgnoreCase("sv01@actvn.edu.vn")).thenReturn(false);
        when(passwordEncoder.encode("StrongPassword@123")).thenReturn("$2a$10$encodedHash");
        when(userRepository.save(any(User.class))).thenReturn(activeStudent);

        UserProfileResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals(12L, response.getId());
        assertEquals("sv01@actvn.edu.vn", response.getEmail());
        assertEquals(UserRole.STUDENT, response.getRole());
        assertEquals(UserStatus.ACTIVE, response.getStatus());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw 409 EMAIL_EXISTS when registering duplicate email")
    void register_DuplicateEmail_ThrowsEmailExists() {
        RegisterRequest request = RegisterRequest.builder()
                .email("sv01@actvn.edu.vn")
                .password("StrongPassword@123")
                .fullName("Nguyễn Văn A")
                .build();

        when(userRepository.existsByEmailIgnoreCase("sv01@actvn.edu.vn")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> authService.register(request));
        assertEquals(ErrorCode.EMAIL_EXISTS, ex.getErrorCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should lock account for 15 minutes after 5 failed login attempts and reject subsequent login")
    void login_FiveFailedAttempts_LocksAccountFor15Minutes() {
        LoginRequest wrongPasswordRequest = LoginRequest.builder()
                .email("sv01@actvn.edu.vn")
                .password("WrongPass!")
                .build();

        when(userRepository.findByEmailIgnoreCase("sv01@actvn.edu.vn")).thenReturn(Optional.of(activeStudent));
        when(passwordEncoder.matches("WrongPass!", activeStudent.getPasswordHash())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        // Attempts 1 to 4: BAD_CREDENTIALS
        for (int attempt = 1; attempt <= 4; attempt++) {
            AppException ex = assertThrows(AppException.class, () -> authService.login(wrongPasswordRequest));
            assertEquals(ErrorCode.BAD_CREDENTIALS, ex.getErrorCode());
            assertEquals(attempt, activeStudent.getFailedLoginCount());
        }

        // Attempt 5: locks account for 15 minutes and throws ACCOUNT_LOCKED
        Instant beforeLock = Instant.now();
        AppException lockEx = assertThrows(AppException.class, () -> authService.login(wrongPasswordRequest));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, lockEx.getErrorCode());
        assertEquals(5, activeStudent.getFailedLoginCount());
        assertNotNull(activeStudent.getLockedUntil());
        assertTrue(activeStudent.getLockedUntil().isAfter(beforeLock.plus(14, ChronoUnit.MINUTES)));

        // Subsequent attempt even with correct password must be rejected immediately with ACCOUNT_LOCKED
        LoginRequest validRequest = LoginRequest.builder()
                .email("sv01@actvn.edu.vn")
                .password("StrongPassword@123")
                .build();
        AppException lockedAgain = assertThrows(AppException.class, () -> authService.login(validRequest));
        assertEquals(ErrorCode.ACCOUNT_LOCKED, lockedAgain.getErrorCode());
        verify(passwordEncoder, never()).matches("StrongPassword@123", activeStudent.getPasswordHash());
    }

    @Test
    @DisplayName("Should issue accessToken and hashed refreshToken on valid login")
    void login_Success_IssuesTokenPair() {
        activeStudent.setFailedLoginCount(2);
        LoginRequest request = LoginRequest.builder()
                .email("sv01@actvn.edu.vn")
                .password("StrongPassword@123")
                .build();

        when(userRepository.findByEmailIgnoreCase("sv01@actvn.edu.vn")).thenReturn(Optional.of(activeStudent));
        when(passwordEncoder.matches("StrongPassword@123", activeStudent.getPasswordHash())).thenReturn(true);
        when(tokenProvider.generateAccessToken(activeStudent)).thenReturn("jwt-access-token");
        when(tokenProvider.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(tokenProvider.hashToken("raw-refresh-token")).thenReturn("sha256-refresh-hash");
        when(tokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(tokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

        AuthResponse response = authService.login(request);

        assertEquals("jwt-access-token", response.getAccessToken());
        assertEquals("raw-refresh-token", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
        assertEquals(0, activeStudent.getFailedLoginCount());

        ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());
        assertEquals("sha256-refresh-hash", tokenCaptor.getValue().getTokenHash());
    }

    @Test
    @DisplayName("Should rotate refresh token: revoke old token and issue new access/refresh pair")
    void refresh_Success_RotatesToken() {
        RefreshToken existingToken = RefreshToken.builder()
                .id(50L)
                .user(activeStudent)
                .tokenHash("old-token-hash")
                .expiresAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .revokedAt(null)
                .build();

        when(tokenProvider.hashToken("old-raw-token")).thenReturn("old-token-hash");
        when(refreshTokenRepository.findWithUserByTokenHash("old-token-hash")).thenReturn(Optional.of(existingToken));
        when(tokenProvider.generateAccessToken(activeStudent)).thenReturn("new-jwt-access");
        when(tokenProvider.generateRefreshToken()).thenReturn("new-raw-refresh");
        when(tokenProvider.hashToken("new-raw-refresh")).thenReturn("new-token-hash");
        when(tokenProvider.getRefreshExpirationMs()).thenReturn(604800000L);
        when(tokenProvider.getAccessExpirationSeconds()).thenReturn(900L);

        TokenRefreshResponse response = authService.refresh(new RefreshTokenRequest("old-raw-token"));

        assertNotNull(response);
        assertEquals("new-jwt-access", response.getAccessToken());
        assertEquals("new-raw-refresh", response.getRefreshToken());
        assertEquals(900L, response.getExpiresIn());
        assertTrue(existingToken.isRevoked());
        verify(refreshTokenRepository).save(existingToken);

        ArgumentCaptor<RefreshToken> savedCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(savedCaptor.capture());
        assertEquals("new-token-hash", savedCaptor.getAllValues().get(1).getTokenHash());
    }

    @Test
    @DisplayName("Should reject already revoked refresh token and revoke all active user tokens")
    void refresh_RevokedToken_ThrowsRefreshTokenInvalid() {
        RefreshToken revokedToken = RefreshToken.builder()
                .id(51L)
                .user(activeStudent)
                .tokenHash("revoked-token-hash")
                .expiresAt(Instant.now().plus(3, ChronoUnit.DAYS))
                .revokedAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .build();

        when(tokenProvider.hashToken("revoked-raw-token")).thenReturn("revoked-token-hash");
        when(refreshTokenRepository.findWithUserByTokenHash("revoked-token-hash")).thenReturn(Optional.of(revokedToken));

        AppException ex = assertThrows(
                AppException.class,
                () -> authService.refresh(new RefreshTokenRequest("revoked-raw-token"))
        );

        assertEquals(ErrorCode.REFRESH_TOKEN_INVALID, ex.getErrorCode());
        verify(refreshTokenRepository).revokeAllActiveByUserId(anyLong(), any(Instant.class));
        verify(tokenProvider, never()).generateAccessToken(any(User.class));
    }
}
