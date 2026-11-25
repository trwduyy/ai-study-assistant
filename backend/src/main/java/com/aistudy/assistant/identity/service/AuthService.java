package com.aistudy.assistant.identity.service;

import com.aistudy.assistant.identity.dto.AuthResponse;
import com.aistudy.assistant.identity.dto.LoginRequest;
import com.aistudy.assistant.identity.dto.LogoutRequest;
import com.aistudy.assistant.identity.dto.RefreshTokenRequest;
import com.aistudy.assistant.identity.dto.RegisterRequest;
import com.aistudy.assistant.identity.dto.TokenRefreshResponse;
import com.aistudy.assistant.identity.dto.UserProfileResponse;

public interface AuthService {

    UserProfileResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    TokenRefreshResponse refresh(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
