package com.bandclub.rehearsal.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import java.util.Set;

public class CookieBearerTokenResolver implements BearerTokenResolver {

    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/auth/signup",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout"
    );

    private final String accessCookieName;

    public CookieBearerTokenResolver(String accessCookieName) {
        this.accessCookieName = accessCookieName;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        if (PUBLIC_AUTH_PATHS.contains(request.getRequestURI())) {
            return null;
        }
        return CookieUtils.find(request, accessCookieName).orElse(null);
    }
}
