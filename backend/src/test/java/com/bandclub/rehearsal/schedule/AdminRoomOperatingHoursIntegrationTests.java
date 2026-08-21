package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.service.AdminReservationService;
import com.bandclub.rehearsal.schedule.service.AdminRoomOperatingHoursService;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class AdminRoomOperatingHoursIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    AdminRoomOperatingHoursService operatingHoursService;

    @Autowired
    AdminReservationService adminReservationService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    ReservationSlotRepository slotRepository;

    @Autowired
    AdminActionLogService actionLogService;

    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void fullDayOverrideSupportsTwentyFourHundredAndRestoreCancelsOutsideDefault() {
        long adminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var round = scheduleService.adminRounds(adminId).stream()
                .filter(candidate -> candidate.startDate().isAfter(today))
                .findFirst()
                .orElseThrow();
        LocalDate date = round.startDate();

        var override = operatingHoursService.override(
                adminId,
                date,
                "00:00",
                "24:00",
                "공연 전날 전일 개방"
        );
        assertEquals(0, override.operatingHours().openMinute());
        assertEquals(1440, override.operatingHours().closeMinute());
        assertTrue(override.operatingHours().overridden());

        var song = songService.createSong(
                adminId,
                "심야 합주 테스트",
                adminId,
                "기타"
        );
        var reservation = adminReservationService.create(
                adminId,
                song.id(),
                at(date, 23, 0),
                60,
                "전일 개방 시간 직접 배정"
        );

        var restored = operatingHoursService.restoreDefault(
                adminId,
                date,
                "일반 운영시간 복귀"
        );
        assertFalse(restored.operatingHours().overridden());
        assertEquals(600, restored.operatingHours().openMinute());
        assertEquals(1320, restored.operatingHours().closeMinute());
        assertTrue(restored.canceledReservationIds().contains(reservation.id()));

        var canceled = reservationRepository.findById(reservation.id()).orElseThrow();
        assertEquals(ReservationStatus.CANCELED, canceled.getStatus());
        assertTrue(canceled.getCancellationReason().contains("일반 운영시간 복귀"));
        assertTrue(slotRepository.findAllByReservationIdInForUpdate(
                java.util.List.of(reservation.id())
        ).isEmpty());

        var logs = actionLogService.list(adminId, 100);
        assertTrue(logs.stream().anyMatch(log ->
                "ROOM_OPERATING_HOURS_OVERRIDE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log ->
                "ROOM_OPERATING_HOURS_RESTORE_DEFAULT".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_CANCELED_BY_OPERATING_HOURS".equals(log.actionType())));
    }

    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(hour, minute)
                .atZone(ScheduleService.SERVICE_ZONE)
                .toInstant();
    }

    private long superAdminId() {
        return userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();
    }
}
