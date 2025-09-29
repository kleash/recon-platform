package com.universal.reconciliation.ingestion.sdk.autoconfig;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
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
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;

    public ReconciliationIngestionProperties(
            @DefaultValue("http://localhost:8080") String baseUrl,
            @NotBlank String username,
            @NotBlank String password,
            @DefaultValue("30s") Duration connectTimeout,
            @DefaultValue("60s") Duration readTimeout,
            @DefaultValue("60s") Duration writeTimeout) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
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

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

}
