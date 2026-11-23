package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.auth.AuthResponse;
import com.aistudy.assistant.dto.auth.RegisterRequest;
import com.aistudy.assistant.dto.user.UserProfileResponse;

public interface AuthService {

    UserProfileResponse register(RegisterRequest request);

    AuthResponse login(com.aistudy.assistant.dto.auth.LoginRequest request);
}
