package com.universal.reconciliation.ingestion.sdk.autoconfig;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties controlling how the ingestion SDK connects to the reconciliation platform.
 */
@Validated
@ConfigurationProperties(prefix = "reconciliation.ingestion")
public class ReconciliationIngestionProperties {

    private final String baseUrl;
    private final String username;
    private final String password;

    public ReconciliationIngestionProperties(
            @DefaultValue("http://localhost:8080") String baseUrl,
            @NotBlank String username,
            @NotBlank String password) {
        this.baseUrl = sanitizeBaseUrl(baseUrl);
        this.username = username;
        this.password = password;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    private static String sanitizeBaseUrl(String raw) {
        if (raw == null || raw.isBlank()) {
            return "http://localhost:8080";
        }
        String trimmed = raw.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
