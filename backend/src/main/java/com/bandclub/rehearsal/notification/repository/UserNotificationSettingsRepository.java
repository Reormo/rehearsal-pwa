package com.bandclub.rehearsal.notification.repository;

import com.bandclub.rehearsal.notification.domain.UserNotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserNotificationSettingsRepository
        extends JpaRepository<UserNotificationSettings, Long> {
}
