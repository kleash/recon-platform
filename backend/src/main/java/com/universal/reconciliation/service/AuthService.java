package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.LoginRequest;
import com.universal.reconciliation.domain.dto.LoginResponse;
import com.universal.reconciliation.security.JwtService;
import java.util.List;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Coordinates the login flow: authenticate via LDAP and issue a JWT.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDirectoryService userDirectoryService;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserDirectoryService userDirectoryService,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.userDirectoryService = userDirectoryService;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        String username = authentication.getName();
        List<String> groups = userDirectoryService.findGroups(username);
        String displayName = userDirectoryService.lookupDisplayName(username);
        String token = jwtService.generateToken(username, groups, displayName);
        return new LoginResponse(token, displayName, groups);
    }
}
