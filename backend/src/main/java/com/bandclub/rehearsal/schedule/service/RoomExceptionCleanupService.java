package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.schedule.repository.RoomExceptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class RoomExceptionCleanupService {

    private final RoomExceptionRepository repository;
    private final Clock clock;

    public RoomExceptionCleanupService(
            RoomExceptionRepository repository,
            Clock clock
    ) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public long deleteExpired() {
        LocalDate today = LocalDate.now(clock.withZone(ScheduleService.SERVICE_ZONE));
        return repository.deleteByExceptionDateBefore(today);
    }
}
