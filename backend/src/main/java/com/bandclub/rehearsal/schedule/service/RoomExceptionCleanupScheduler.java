package com.bandclub.rehearsal.schedule.service;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RoomExceptionCleanupScheduler {

    private final RoomExceptionCleanupService cleanupService;

    public RoomExceptionCleanupScheduler(RoomExceptionCleanupService cleanupService) {
        this.cleanupService = cleanupService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void cleanupOnStartup() {
        cleanupService.deleteExpired();
    }

    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Seoul")
    public void cleanupAfterMidnight() {
        cleanupService.deleteExpired();
    }
}
