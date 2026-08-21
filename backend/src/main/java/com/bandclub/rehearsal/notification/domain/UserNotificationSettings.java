package com.bandclub.rehearsal.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_notification_settings")
public class UserNotificationSettings {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "rehearsal_reminder_minutes")
    private Short rehearsalReminderMinutes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserNotificationSettings() {
    }

    private UserNotificationSettings(
            Long userId,
            Integer rehearsalReminderMinutes,
            Instant now
    ) {
        this.userId = userId;
        this.rehearsalReminderMinutes = toShort(rehearsalReminderMinutes);
        this.updatedAt = now;
    }

    public static UserNotificationSettings defaults(Long userId, Instant now) {
        return new UserNotificationSettings(userId, 30, now);
    }

    public void changeReminder(Integer minutes, Instant now) {
        this.rehearsalReminderMinutes = toShort(minutes);
        this.updatedAt = now;
    }

    private static Short toShort(Integer value) {
        return value == null ? null : value.shortValue();
    }

    public Long getUserId() {
        return userId;
    }

    public Integer getRehearsalReminderMinutes() {
        return rehearsalReminderMinutes == null
                ? null
                : rehearsalReminderMinutes.intValue();
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
