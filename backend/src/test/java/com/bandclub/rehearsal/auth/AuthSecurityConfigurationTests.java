package com.bandclub.rehearsal.auth;

import com.bandclub.rehearsal.auth.config.AuthProperties;
import com.bandclub.rehearsal.auth.security.CookieBearerTokenResolver;
import com.bandclub.rehearsal.auth.service.AuthCookieService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSecurityConfigurationTests {

    @Test
    void authCookiesKeepSecurityAttributesAndScopedRefreshPath() {
        AuthCookieService cookieService =
                new AuthCookieService(properties(true));

        String access = cookieService.access("access-value").toString();
        String refresh = cookieService.refresh("refresh-value").toString();

        assertTrue(access.contains("HttpOnly"));
        assertTrue(access.contains("Secure"));
        assertTrue(access.contains("SameSite=Lax"));
        assertTrue(access.contains("Path=/"));

        assertTrue(refresh.contains("HttpOnly"));
        assertTrue(refresh.contains("Secure"));
        assertTrue(refresh.contains("SameSite=Lax"));
        assertTrue(refresh.contains("Path=/api/auth"));
    }

    @Test
    void publicAuthPathsIgnoreStaleAccessCookie() {
        CookieBearerTokenResolver resolver =
                new CookieBearerTokenResolver("access_token");

        for (String path : new String[]{
                "/api/auth/signup",
                "/api/auth/login",
                "/api/auth/refresh",
                "/api/auth/logout"
        }) {
            MockHttpServletRequest request = request(path, "stale-token");
            assertNull(resolver.resolve(request), path);
        }
    }

    @Test
    void protectedPathResolvesAccessCookie() {
        CookieBearerTokenResolver resolver =
                new CookieBearerTokenResolver("access_token");

        assertEquals(
                "valid-token",
                resolver.resolve(request("/api/auth/me", "valid-token"))
        );
    }

    private MockHttpServletRequest request(String path, String accessToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setCookies(new Cookie("access_token", accessToken));
        return request;
    }

    private AuthProperties properties(boolean secure) {
        return new AuthProperties(
                "0123456789abcdef0123456789abcdef",
                "rehearsal-test",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "access_token",
                "refresh_token",
                secure,
                "Lax",
                "http://localhost:3000"
        );
    }
}
