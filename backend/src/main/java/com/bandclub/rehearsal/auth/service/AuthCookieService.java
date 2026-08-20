package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.config.AuthProperties;
import com.bandclub.rehearsal.auth.security.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class AuthCookieService {

    private final AuthProperties properties;

    public AuthCookieService(AuthProperties properties) {
        this.properties = properties;
    }

    public ResponseCookie access(String token) {
        return cookie(
                properties.accessCookieName(),
                token,
                "/",
                properties.accessTokenTtl()
        );
    }

    public ResponseCookie refresh(String token) {
        return cookie(
                properties.refreshCookieName(),
                token,
                "/api/auth",
                properties.refreshTokenTtl()
        );
    }

    public ResponseCookie clearAccess() {
        return cookie(properties.accessCookieName(), "", "/", Duration.ZERO);
    }

    public ResponseCookie clearRefresh() {
        return cookie(properties.refreshCookieName(), "", "/api/auth", Duration.ZERO);
    }

    public Optional<String> refreshToken(HttpServletRequest request) {
        return CookieUtils.find(request, properties.refreshCookieName());
    }

    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite(properties.cookieSameSite())
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
