package com.bandclub.rehearsal.auth;

import com.bandclub.rehearsal.auth.security.LoginAttemptLimiter;
import com.bandclub.rehearsal.common.exception.AppException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoginAttemptLimiterTests {

    @Test
    void repeatedLoginAttemptsAreRejectedWithoutRevealingAccountExistence() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

        for (int i = 0; i < 8; i++) {
            assertDoesNotThrow(() -> limiter.checkAndRecord("member01", "127.0.0.1"));
        }

        AppException blocked = assertThrows(AppException.class,
                () -> limiter.checkAndRecord("member01", "127.0.0.1"));
        assertEquals("LOGIN_RATE_LIMITED", blocked.getCode());

        // One account's blocked attempts do not block other accounts on the same network.
        assertDoesNotThrow(() -> limiter.checkAndRecord("member02", "127.0.0.1"));
        assertDoesNotThrow(() -> limiter.checkAndRecord("member01", "127.0.0.2"));
    }

    @Test
    void allowanceResetsAfterTheTenMinuteWindow() {
        MutableClock clock = new MutableClock();
        LoginAttemptLimiter limiter = new LoginAttemptLimiter(clock);

        for (int i = 0; i < 8; i++) {
            limiter.checkAndRecord("member01", "127.0.0.1");
        }
        assertThrows(AppException.class,
                () -> limiter.checkAndRecord("member01", "127.0.0.1"));

        clock.advance(Duration.ofMinutes(10));
        assertDoesNotThrow(() -> limiter.checkAndRecord("member01", "127.0.0.1"));
    }

    private static final class MutableClock extends Clock {
        private Instant current = Instant.parse("2026-11-09T00:00:00Z");

        void advance(Duration duration) {
            current = current.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(current, zone);
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
