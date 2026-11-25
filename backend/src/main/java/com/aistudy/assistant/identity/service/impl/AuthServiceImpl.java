package com.aistudy.assistant.identity.service.impl;

import com.aistudy.assistant.common.exception.AppException;
import com.aistudy.assistant.common.exception.ErrorCode;
import com.aistudy.assistant.identity.dto.AuthResponse;
import com.aistudy.assistant.identity.dto.LoginRequest;
import com.aistudy.assistant.identity.dto.LogoutRequest;
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
import com.aistudy.assistant.identity.security.SecurityUtils;
import com.aistudy.assistant.identity.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Handles V2 user registration, login with account lockout, refresh token rotation, and logout revocation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    public static final int MAX_FAILED_LOGIN_ATTEMPTS = 5;
    public static final Duration ACCOUNT_LOCK_DURATION = Duration.ofMinutes(15);

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public UserProfileResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName().trim())
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)
                .failedLoginCount(0)
                .build();

        try {
            User savedUser = userRepository.save(user);
            log.info("Registered new student account id={}", savedUser.getId());
            return userMapper.toProfileResponse(savedUser);
        } catch (DataIntegrityViolationException ex) {
            throw new AppException(ErrorCode.EMAIL_EXISTS);
        }
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        Instant now = Instant.now();

        User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
                .orElseThrow(() -> new AppException(ErrorCode.BAD_CREDENTIALS));

        if (user.getStatus() == UserStatus.LOCKED) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED, "Tài khoản của bạn đã bị khóa bởi quản trị viên.");
        }

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now)) {
                throw new AppException(ErrorCode.ACCOUNT_LOCKED);
            }
            user.resetFailedLogins();
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            user.recordFailedLogin(now, MAX_FAILED_LOGIN_ATTEMPTS, ACCOUNT_LOCK_DURATION);
            userRepository.save(user);

            if (user.getFailedLoginCount() >= MAX_FAILED_LOGIN_ATTEMPTS) {
                log.warn("Account id={} temporarily locked for 15 minutes due to repeated failed logins", user.getId());
                throw new AppException(
                        ErrorCode.ACCOUNT_LOCKED,
                        "Bạn đã nhập sai mật khẩu 5 lần. Tài khoản tạm thời bị khóa trong 15 phút."
                );
            }
            throw new AppException(ErrorCode.BAD_CREDENTIALS);
        }

        if ((user.getFailedLoginCount() != null && user.getFailedLoginCount() > 0) || user.getLockedUntil() != null) {
            user.resetFailedLogins();
            userRepository.save(user);
        }

        IssuedTokenPair tokenPair = issueAndPersistTokenPair(user, now);
        log.info("User id={} logged in successfully", user.getId());

        return AuthResponse.builder()
                .accessToken(tokenPair.accessToken())
                .refreshToken(tokenPair.rawRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .user(userMapper.toAuthUserResponse(user))
                .build();
    }

    @Override
    @Transactional(noRollbackFor = AppException.class)
    public TokenRefreshResponse refresh(RefreshTokenRequest request) {
        Instant now = Instant.now();
        String incomingHash = tokenProvider.hashToken(request.getRefreshToken().trim());

        RefreshToken storedToken = refreshTokenRepository.findWithUserByTokenHash(incomingHash)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        User user = storedToken.getUser();

        if (storedToken.isRevoked()) {
            log.warn("Detected reuse of revoked refresh token for user id={}, revoking active sessions", user.getId());
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), now);
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        if (storedToken.isExpired(now)) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_INVALID, "Refresh token đã hết hạn. Vui lòng đăng nhập lại.");
        }

        if (user.isCurrentlyLocked(now)) {
            throw new AppException(ErrorCode.ACCOUNT_LOCKED);
        }

        storedToken.revoke(now);
        refreshTokenRepository.save(storedToken);

        IssuedTokenPair rotatedPair = issueAndPersistTokenPair(user, now);
        log.debug("Rotated refresh token for user id={}", user.getId());

        return TokenRefreshResponse.builder()
                .accessToken(rotatedPair.accessToken())
                .refreshToken(rotatedPair.rawRefreshToken())
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .build();
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        Long currentUserId = SecurityUtils.currentUserId();
        String tokenHash = tokenProvider.hashToken(request.getRefreshToken().trim());

        RefreshToken storedToken = refreshTokenRepository.findWithUserByTokenHash(tokenHash)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_INVALID));

        if (!storedToken.getUser().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (!storedToken.isRevoked()) {
            storedToken.revoke(Instant.now());
            refreshTokenRepository.save(storedToken);
        }
        log.info("User id={} logged out and revoked refresh token", currentUserId);
    }

    private IssuedTokenPair issueAndPersistTokenPair(User user, Instant now) {
        String accessToken = tokenProvider.generateAccessToken(user);
        String rawRefreshToken = tokenProvider.generateRefreshToken();
        String refreshTokenHash = tokenProvider.hashToken(rawRefreshToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .expiresAt(now.plusMillis(tokenProvider.getRefreshExpirationMs()))
                .build();
        refreshTokenRepository.save(refreshToken);

        return new IssuedTokenPair(accessToken, rawRefreshToken);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private record IssuedTokenPair(String accessToken, String rawRefreshToken) {
    }
}
