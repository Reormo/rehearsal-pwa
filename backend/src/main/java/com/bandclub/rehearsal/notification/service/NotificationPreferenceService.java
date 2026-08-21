package com.bandclub.rehearsal.notification.service;

import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.domain.UserNotificationSettings;
import com.bandclub.rehearsal.notification.repository.UserNotificationSettingsRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Set;

@Service
public class NotificationPreferenceService {

    private static final Set<Integer> ALLOWED_REMINDER_MINUTES =
            Set.of(10, 30, 60, 120, 1440);

    private final MembershipService membershipService;
    private final UserNotificationSettingsRepository repository;
    private final Clock clock;

    public NotificationPreferenceService(
            MembershipService membershipService,
            UserNotificationSettingsRepository repository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public SettingsView get(Long userId) {
        membershipService.requireMembership(userId);
        return toView(requireOrCreate(userId));
    }

    @Transactional
    public SettingsView update(Long userId, Integer rehearsalReminderMinutes) {
        membershipService.requireMembership(userId);
        validateReminder(rehearsalReminderMinutes);

        UserNotificationSettings settings = requireOrCreate(userId);
        settings.changeReminder(rehearsalReminderMinutes, clock.instant());
        return toView(settings);
    }

    private UserNotificationSettings requireOrCreate(Long userId) {
        return repository.findById(userId)
                .orElseGet(() -> repository.save(
                        UserNotificationSettings.defaults(
                                userId,
                                clock.instant()
                        )
                ));
    }

    private void validateReminder(Integer minutes) {
        if (minutes != null && !ALLOWED_REMINDER_MINUTES.contains(minutes)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_REHEARSAL_REMINDER",
                    "합주 리마인더는 끄기, 10분, 30분, 1시간, 2시간, 하루 전 중에서 선택해주세요."
            );
        }
    }

    private SettingsView toView(UserNotificationSettings settings) {
        return new SettingsView(
                settings.getRehearsalReminderMinutes(),
                settings.getUpdatedAt()
        );
    }

    public record SettingsView(
            Integer rehearsalReminderMinutes,
            java.time.Instant updatedAt
    ) {
    }
}
