package com.bandclub.rehearsal.notification;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.repository.PushSubscriptionRepository;
import com.bandclub.rehearsal.notification.service.NotificationPreferenceService;
import com.bandclub.rehearsal.notification.service.PushSubscriptionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class NotificationPreferenceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    NotificationPreferenceService preferenceService;

    @Autowired
    PushSubscriptionService pushSubscriptionService;

    @Autowired
    PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void defaultReminderIsThirtyMinutesAndAllowedValuesCanBeChanged() {
        long userId = superAdminId();

        assertEquals(
                30,
                preferenceService.get(userId).rehearsalReminderMinutes()
        );

        assertEquals(
                120,
                preferenceService.update(userId, 120)
                        .rehearsalReminderMinutes()
        );

        assertNull(
                preferenceService.update(userId, null)
                        .rehearsalReminderMinutes()
        );

        assertThrows(
                AppException.class,
                () -> preferenceService.update(userId, 15)
        );
    }

    @Test
    void pushSubscriptionCanBeRegisteredDisabledAndReactivated() {
        long userId = superAdminId();
        String endpoint = "https://push.example.test/subscription-1";

        pushSubscriptionService.subscribe(
                userId,
                endpoint,
                "p256dh-key",
                "auth-key",
                "JUnit"
        );

        var first = pushSubscriptionRepository
                .findByEndpoint(endpoint)
                .orElseThrow();

        assertEquals(userId, first.getUserId());
        assertNull(first.getDisabledAt());
        assertEquals(1, pushSubscriptionService.activeCount(userId));

        pushSubscriptionService.unsubscribe(userId, endpoint);

        var disabled = pushSubscriptionRepository
                .findByEndpoint(endpoint)
                .orElseThrow();
        assertTrue(disabled.getDisabledAt() != null);
        assertEquals(0, pushSubscriptionService.activeCount(userId));

        pushSubscriptionService.subscribe(
                userId,
                endpoint,
                "new-p256dh-key",
                "new-auth-key",
                "JUnit 2"
        );

        var reactivated = pushSubscriptionRepository
                .findByEndpoint(endpoint)
                .orElseThrow();
        assertNull(reactivated.getDisabledAt());
        assertEquals("new-p256dh-key", reactivated.getP256dhKey());
        assertEquals(1, pushSubscriptionService.activeCount(userId));
    }

    private long superAdminId() {
        return userRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
