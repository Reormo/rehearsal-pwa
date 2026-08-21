package com.bandclub.rehearsal.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@ConditionalOnProperty(
        prefix = "app.notifications",
        name = "scheduler-enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class NotificationScheduler {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationScheduler.class);

    private final NotificationScheduleService service;
    private final Clock clock;

    public NotificationScheduler(
            NotificationScheduleService service,
            Clock clock
    ) {
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(
            fixedDelayString = "${app.notifications.scheduler-delay-ms:30000}",
            initialDelayString = "${app.notifications.scheduler-initial-delay-ms:10000}"
    )
    public void run() {
        try {
            var result = service.process(clock.instant());
            int created =
                    result.bookingPreOpenCreated()
                            + result.bookingOpenCreated()
                            + result.rehearsalRemindersCreated();

            if (created > 0) {
                log.info(
                        "Notification scheduler created {} notifications: preOpen={}, open={}, reminders={}",
                        created,
                        result.bookingPreOpenCreated(),
                        result.bookingOpenCreated(),
                        result.rehearsalRemindersCreated()
                );
            }
        } catch (RuntimeException exception) {
            log.error("Notification scheduler failed.", exception);
        }
    }
}
