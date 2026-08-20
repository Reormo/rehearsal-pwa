package com.bandclub.rehearsal.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String accessCookieName,
        String refreshCookieName,
        boolean cookieSecure,
        String cookieSameSite,
        String frontendOrigin
) {
    private static final Set<String> ALLOWED_SAME_SITE = Set.of("Strict", "Lax", "None");

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("app.auth.jwt-secret must be at least 32 bytes.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("app.auth.issuer is required.");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.auth.access-token-ttl must be positive.");
        }
        if (refreshTokenTtl == null || refreshTokenTtl.isZero() || refreshTokenTtl.isNegative()) {
            throw new IllegalArgumentException("app.auth.refresh-token-ttl must be positive.");
        }
        if (accessCookieName == null || accessCookieName.isBlank()) {
            throw new IllegalArgumentException("app.auth.access-cookie-name is required.");
        }
        if (refreshCookieName == null || refreshCookieName.isBlank()) {
            throw new IllegalArgumentException("app.auth.refresh-cookie-name is required.");
        }
        if (!ALLOWED_SAME_SITE.contains(cookieSameSite)) {
            throw new IllegalArgumentException("app.auth.cookie-same-site must be Strict, Lax, or None.");
        }
        if (frontendOrigin == null || frontendOrigin.isBlank()) {
            throw new IllegalArgumentException("app.auth.frontend-origin is required.");
        }
    }
}
