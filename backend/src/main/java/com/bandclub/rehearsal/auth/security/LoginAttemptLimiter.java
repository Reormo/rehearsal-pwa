package com.bandclub.rehearsal.auth.security;

import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Single-instance guard against rapid login guessing.
 * Limits attempts per login identifier and observed peer address, not all users behind one NAT.
 * Multi-instance deployments must additionally enforce a shared limit at the ingress.
 */
@Component
public class LoginAttemptLimiter {
    private static final int MAX_ATTEMPTS = 8;
    private static final int MAX_KEYS = 20_000;
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public LoginAttemptLimiter(Clock clock) {
        this.clock = clock;
    }

    public void checkAndRecord(String loginId, String remoteAddress) {
        String normalizedLogin = loginId.trim().toLowerCase(Locale.ROOT);
        String address = remoteAddress == null ? "unknown" : remoteAddress;
        String key = address.length() + ":" + address + ":" + normalizedLogin;
        Instant now = clock.instant();

        // Cap the amount of memory an attacker can consume with distinct identifiers.
        if (!windows.containsKey(key) && windows.size() >= MAX_KEYS) {
            windows.entrySet().removeIf(entry ->
                    !now.isBefore(entry.getValue().startedAt().plus(WINDOW)));
            if (windows.size() >= MAX_KEYS) {
                throw limited();
            }
        }

        AtomicBoolean blocked = new AtomicBoolean(false);
        windows.compute(key, (ignored, previous) -> {
            if (previous == null || !now.isBefore(previous.startedAt().plus(WINDOW))) {
                return new Window(now, 1);
            }
            if (previous.count() >= MAX_ATTEMPTS) {
                blocked.set(true);
                return previous;
            }
            return new Window(previous.startedAt(), previous.count() + 1);
        });

        if (blocked.get()) {
            throw limited();
        }
    }

    private AppException limited() {
        return new AppException(
                HttpStatus.TOO_MANY_REQUESTS,
                "LOGIN_RATE_LIMITED",
                "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해주세요."
        );
    }

    private record Window(Instant startedAt, int count) {
    }
}
