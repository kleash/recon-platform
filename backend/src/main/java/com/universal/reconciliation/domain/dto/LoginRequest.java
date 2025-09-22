package com.universal.reconciliation.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for the authentication endpoint.
 */
public record LoginRequest(
        @NotBlank(message = "Username is required") String username,
        @NotBlank(message = "Password is required") String password) {
}
