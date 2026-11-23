package com.aistudy.assistant.service;

import com.aistudy.assistant.dto.auth.RegisterRequest;
import com.aistudy.assistant.dto.user.UserProfileResponse;
import com.aistudy.assistant.entity.User;
import com.aistudy.assistant.exception.DuplicateResourceException;
import com.aistudy.assistant.repository.UserRepository;
import com.aistudy.assistant.security.JwtTokenProvider;
import com.aistudy.assistant.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider tokenProvider;

    @InjectMocks
    private AuthServiceImpl authService;

    private RegisterRequest registerRequest;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterRequest.builder()
                .username("teststudent")
                .email("teststudent@example.com")
                .password("TestPassword123")
                .fullName("Test Student")
                .build();
    }

    @Test
    @DisplayName("Should successfully register a new user")
    void register_Success() {
        when(userRepository.existsByUsername(registerRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(registerRequest.getPassword())).thenReturn("encodedPassword123");

        User savedUser = User.builder()
                .id(1L)
                .username(registerRequest.getUsername())
                .email(registerRequest.getEmail())
                .passwordHash("encodedPassword123")
                .fullName(registerRequest.getFullName())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserProfileResponse response = authService.register(registerRequest);

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals(registerRequest.getUsername(), response.getUsername());
        assertEquals(registerRequest.getEmail(), response.getEmail());
        assertEquals(registerRequest.getFullName(), response.getFullName());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when username already exists")
    void register_DuplicateUsername_ThrowsException() {
        when(userRepository.existsByUsername(registerRequest.getUsername())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should throw DuplicateResourceException when email already exists")
    void register_DuplicateEmail_ThrowsException() {
        when(userRepository.existsByUsername(registerRequest.getUsername())).thenReturn(false);
        when(userRepository.existsByEmail(registerRequest.getEmail())).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> authService.register(registerRequest));
        verify(userRepository, never()).save(any(User.class));
    }
}
