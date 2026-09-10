package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.service.BookingService;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import com.bandclub.rehearsal.schedule.service.StageBookingWindowService;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class StageReservationDurationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    StageBookingWindowService stageBookingWindowService;

    @Autowired
    SongService songService;

    @Autowired
    BookingService bookingService;

    @Autowired
    UserRepository userRepository;

    @Test
    void stageTypesCanUseDifferentBookingWindowsAndMaxDurations() {
        long superAdminId = superAdminId();

        var round = scheduleService.adminRounds(superAdminId).get(1);
        LocalDate bookingDate = round.startDate();
        Instant roundClose = round.endDate()
                .atTime(ScheduleService.DEFAULT_CLOSE_TIME)
                .atZone(ScheduleService.SERVICE_ZONE)
                .toInstant();

        var typeOneSong = songService.createSong(
                superAdminId,
                "종류 1 예약 정책 테스트",
                "종류 1",
                superAdminId,
                "기타"
        );
        var typeTwoSong = songService.createSong(
                superAdminId,
                "종류 2 예약 정책 테스트",
                "종류 2",
                superAdminId,
                "기타"
        );

        stageBookingWindowService.upsert(
                superAdminId,
                round.id(),
                typeOneSong.stageTypeId(),
                Instant.now().minusSeconds(60),
                roundClose,
                60
        );
        stageBookingWindowService.upsert(
                superAdminId,
                round.id(),
                typeTwoSong.stageTypeId(),
                Instant.now().minusSeconds(60),
                roundClose,
                120
        );

        var typeOneThirty = bookingService.options(
                superAdminId,
                bookingDate,
                30,
                typeOneSong.id()
        );
        var typeTwoThirty = bookingService.options(
                superAdminId,
                bookingDate,
                30,
                typeTwoSong.id()
        );

        assertEquals(60, typeOneThirty.maxReservationMinutes());
        assertEquals(120, typeTwoThirty.maxReservationMinutes());

        assertThrows(
                AppException.class,
                () -> bookingService.options(
                        superAdminId,
                        bookingDate,
                        90,
                        typeOneSong.id()
                )
        );

        assertEquals(
                120,
                bookingService.options(
                        superAdminId,
                        bookingDate,
                        120,
                        typeTwoSong.id()
                ).maxReservationMinutes()
        );
    }

    private long superAdminId() {
        return userRepository
                .findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();
    }
}
