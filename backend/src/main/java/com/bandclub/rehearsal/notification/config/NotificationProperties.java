package com.bandclub.rehearsal.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(
        boolean schedulerEnabled,
        long schedulerDelayMs,
        long schedulerInitialDelayMs,
        String vapidPublicKey,
        String vapidPrivateKey,
        String vapidSubject
) {
    public boolean webPushEnabled() {
        return hasText(vapidPublicKey)
                && hasText(vapidPrivateKey)
                && hasText(vapidSubject);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
