package com.universal.reconciliation.security;

import com.universal.reconciliation.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Handles JWT creation and parsing.
 */
@Component
public class JwtService {

    private final JwtProperties properties;
    private Key signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a signed JWT for the supplied subject and claims.
     */
    public String generateToken(String subject, List<String> groups, String displayName) {
        Instant now = Instant.now();
        Instant expiry = now.plusSeconds(properties.getExpirationSeconds());
        return Jwts.builder()
                .setSubject(subject)
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(expiry))
                .addClaims(Map.of("groups", groups, "displayName", displayName))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * Parses a JWT and returns the associated claims if valid.
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSigningKey() {
        if (signingKey == null) {
            String secret = properties.getSecret();
            if (!StringUtils.hasText(secret)) {
                throw new IllegalStateException("JWT secret is not configured");
            }
            signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        }
        return signingKey;
    }
}
