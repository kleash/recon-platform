package com.universal.reconciliation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.universal.reconciliation.domain.dto.LoginRequest;
import com.universal.reconciliation.domain.dto.LoginResponse;
import com.universal.reconciliation.security.JwtService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

class AuthServiceTest {

    @Test
    void login_allowsHarnessCredentialsWhenAuthenticationFails() {
        AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));

        UserDirectoryService userDirectoryService = Mockito.mock(UserDirectoryService.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        when(jwtService.generateToken("ops1", List.of("recon-makers", "recon-checkers"), "Operations User"))
                .thenReturn("token-123");

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("example-harness");

        AuthService authService =
                new AuthService(authenticationManager, userDirectoryService, jwtService, environment);

        LoginResponse response = authService.login(new LoginRequest("ops1", "password"));

        assertThat(response.token()).isEqualTo("token-123");
        assertThat(response.groups()).containsExactly("recon-makers", "recon-checkers");
        verify(jwtService)
                .generateToken("ops1", List.of("recon-makers", "recon-checkers"), "Operations User");
        verifyNoInteractions(userDirectoryService);
    }

    @Test
    void login_rethrowsWhenHarnessCredentialsDoNotMatch() {
        AuthenticationManager authenticationManager = Mockito.mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("invalid"));

        UserDirectoryService userDirectoryService = Mockito.mock(UserDirectoryService.class);
        JwtService jwtService = Mockito.mock(JwtService.class);

        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("example-harness");

        AuthService authService =
                new AuthService(authenticationManager, userDirectoryService, jwtService, environment);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ops1", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
        verifyNoInteractions(jwtService);
    }
}
