package com.universal.reconciliation.domain.dto;

import java.util.List;

/**
 * Lightweight JWT response after LDAP authentication.
 */
public record LoginResponse(String token, String displayName, List<String> groups) {
}
