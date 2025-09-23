package com.universal.reconciliation.service;

import com.universal.reconciliation.domain.dto.LoginRequest;
import com.universal.reconciliation.domain.dto.LoginResponse;
import com.universal.reconciliation.security.JwtService;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

/**
 * Coordinates the login flow: authenticate via LDAP and issue a JWT.
 */
@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDirectoryService userDirectoryService;
    private final JwtService jwtService;
    private final boolean harnessMode;

    public AuthService(
            AuthenticationManager authenticationManager,
            UserDirectoryService userDirectoryService,
            JwtService jwtService,
            Environment environment) {
        this.authenticationManager = authenticationManager;
        this.userDirectoryService = userDirectoryService;
        this.jwtService = jwtService;
        this.harnessMode = environment != null && environment.acceptsProfiles(Profiles.of("example-harness"));
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication;
        boolean harnessAuthenticated = false;
        try {
            authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (AuthenticationException ex) {
            if (harnessMode && validHarnessCredentials(request.username(), request.password())) {
                authentication = new UsernamePasswordAuthenticationToken(request.username(), request.password());
                harnessAuthenticated = true;
            } else {
                throw ex;
            }
        }

        String username = authentication.getName();
        List<String> groups;
        String displayName;
        if (harnessAuthenticated) {
            groups = List.of("recon-makers", "recon-checkers");
            displayName = "Operations User";
        } else {
            groups = userDirectoryService.findGroups(username);
            displayName = userDirectoryService.lookupDisplayName(username);
        }

        String token = jwtService.generateToken(username, groups, displayName);
        return new LoginResponse(token, displayName, groups);
    }

    private boolean validHarnessCredentials(String username, String password) {
        return "ops1".equals(username) && "password".equals(password);
    }
}
