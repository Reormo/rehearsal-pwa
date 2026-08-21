package com.bandclub.rehearsal.realtime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RealtimeMutationInterceptorTests {

    private final RealtimeSchedulePublisher publisher =
            mock(RealtimeSchedulePublisher.class);
    private final RealtimeMutationInterceptor interceptor =
            new RealtimeMutationInterceptor(publisher);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulScheduleMutationPublishesForAuthenticatedUser() {
        SecurityContextHolder.getContext().setAuthentication(authentication(42L));

        MockHttpServletRequest request =
                new MockHttpServletRequest("PATCH", "/api/reservations/9/move");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(publisher).publishForUser(42L);
    }

    @Test
    void failedMutationDoesNotPublish() {
        SecurityContextHolder.getContext().setAuthentication(authentication(42L));

        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/swaps");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(409);

        interceptor.afterCompletion(request, response, new Object(), null);

        verify(publisher, never()).publishForUser(42L);
    }

    @Test
    void onlyScheduleMutationPathsAreMatched() {
        assertTrue(interceptor.shouldPublish("POST", "/api/reservations"));
        assertTrue(interceptor.shouldPublish("PATCH", "/api/admin/schedule/rounds/1"));
        assertTrue(interceptor.shouldPublish("POST", "/api/admin/swaps/1/accept"));
        assertFalse(interceptor.shouldPublish("GET", "/api/schedule/days/2026-08-24"));
        assertFalse(interceptor.shouldPublish("POST", "/api/admin/announcements"));
    }

    private JwtAuthenticationToken authentication(long userId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(300),
                java.util.Map.of("alg", "HS256"),
                java.util.Map.of("sub", Long.toString(userId))
        );
        return new JwtAuthenticationToken(jwt, List.of(), "tester");
    }
}
