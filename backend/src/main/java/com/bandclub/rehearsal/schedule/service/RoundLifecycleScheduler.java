package com.bandclub.rehearsal.schedule.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RoundLifecycleScheduler {

    private final RoundLifecycleService lifecycleService;

    public RoundLifecycleScheduler(
            RoundLifecycleService lifecycleService
    ) {
        this.lifecycleService = lifecycleService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        lifecycleService.reconcileAllClubs();
    }

    @Scheduled(
            cron = "0 0 0 * * MON",
            zone = "Asia/Seoul"
    )
    public void rolloverAtMondayMidnight() {
        lifecycleService.reconcileAllClubs();
    }
}
