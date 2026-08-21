package com.bandclub.rehearsal.realtime;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

@Component
public class RealtimeMutationInterceptor implements HandlerInterceptor {

    private static final Logger log =
            LoggerFactory.getLogger(RealtimeMutationInterceptor.class);

    private static final Set<String> MUTATION_METHODS =
            Set.of("POST", "PUT", "PATCH", "DELETE");

    private final RealtimeSchedulePublisher publisher;

    public RealtimeMutationInterceptor(RealtimeSchedulePublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception exception
    ) {
        if (exception != null
                || response.getStatus() >= 400
                || !shouldPublish(request.getMethod(), request.getRequestURI())) {
            return;
        }

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return;
        }

        try {
            long userId = Long.parseLong(
                    jwtAuthentication.getToken().getSubject()
            );
            publisher.publishForUser(userId);
        } catch (RuntimeException publishError) {
            log.warn(
                    "Failed to publish realtime schedule event for {} {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    publishError
            );
        }
    }

    boolean shouldPublish(String method, String uri) {
        if (!MUTATION_METHODS.contains(method)) {
            return false;
        }

        return uri.equals("/api/reservations")
                || uri.startsWith("/api/reservations/")
                || uri.startsWith("/api/admin/reservations")
                || uri.startsWith("/api/swaps")
                || uri.startsWith("/api/admin/swaps")
                || uri.startsWith("/api/admin/schedule");
    }
}
