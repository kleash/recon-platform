package com.universal.reconciliation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Binds JWT configuration values from application properties.
 */
@Component
@ConfigurationProperties(prefix = "app.security.jwt")
public class JwtProperties {

    /** Secret key used to sign tokens. */
    private String secret;

    /** Token validity duration in seconds. */
    private long expirationSeconds;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    public void setExpirationSeconds(long expirationSeconds) {
        this.expirationSeconds = expirationSeconds;
    }
}
