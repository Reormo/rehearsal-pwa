package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ScheduleIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    AdminActionLogService actionLogService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ReservationSlotRepository slotRepository;

    @Test
    @Transactional
    void atomicSlotsStayAtThirtyMinutesWhileDisplayedSlotsFollowMaxMinutes() {
        long superAdminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        var settings = scheduleService.adminSettings(superAdminId);
        assertFalse(settings.allowMultipleReservations());
        assertEquals(1680, settings.defaultBookingOpenLeadMinutes());
        assertEquals(90, settings.defaultMaxReservationMinutes());

        var round = scheduleService.adminRounds(superAdminId).stream()
                .filter(candidate -> !today.isBefore(candidate.startDate()) && !today.isAfter(candidate.endDate()))
                .findFirst()
                .orElseThrow();

        Instant from = today.atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        Instant to = today.plusDays(1).atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        var atomicSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        from,
                        to
                );
        assertEquals(24, atomicSlots.size());

        var initialDay = scheduleService.day(superAdminId, today);
        assertEquals(8, initialDay.standardSlots().size());
        assertTrue(initialDay.remainderSlots().isEmpty());

        scheduleService.updateRound(
                superAdminId,
                round.id(),
                round.bookingOpenAt(),
                60
        );
        assertEquals(12, scheduleService.day(superAdminId, today).standardSlots().size());

        atomicSlots.get(0).occupy(1001L);
        atomicSlots.get(1).occupy(1001L);
        slotRepository.flush();

        scheduleService.updateRound(
                superAdminId,
                round.id(),
                round.bookingOpenAt(),
                30
        );
        var thirtyMinuteDay = scheduleService.day(superAdminId, today);
        assertEquals(22, thirtyMinuteDay.standardSlots().size());
        assertEquals(1, thirtyMinuteDay.unavailableSlots().size());
        assertEquals(60, Duration.between(
                thirtyMinuteDay.unavailableSlots().getFirst().startAt(),
                thirtyMinuteDay.unavailableSlots().getFirst().endAt()
        ).toMinutes());

        atomicSlots.get(0).release();
        atomicSlots.get(1).release();
        atomicSlots.get(2).occupy(1002L);
        slotRepository.flush();

        scheduleService.updateRound(
                superAdminId,
                round.id(),
                round.bookingOpenAt(),
                90
        );
        var ninetyMinuteDay = scheduleService.day(superAdminId, today);
        assertEquals(7, ninetyMinuteDay.standardSlots().size());
        assertEquals(1, ninetyMinuteDay.remainderSlots().size());
        assertEquals(60, ninetyMinuteDay.remainderSlots().getFirst().durationMinutes());
        assertEquals(1, ninetyMinuteDay.unavailableSlots().size());
    }

    @Test
    @Transactional
    void multipleBlockedPeriodsCreateRemainderSlotsAndOnlyBlockThoseTimes() {
        long superAdminId = superAdminId();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);

        scheduleService.createException(
                superAdminId,
                date,
                LocalTime.of(13, 0),
                LocalTime.of(14, 0),
                "장비 점검"
        );
        scheduleService.createException(
                superAdminId,
                date,
                LocalTime.of(16, 0),
                LocalTime.of(17, 0),
                "수업"
        );

        var day = scheduleService.day(superAdminId, date);
        assertEquals(ScheduleService.RoomStatus.PARTIAL_BLOCKED, day.roomStatus());
        assertEquals(2, day.blockedPeriods().size());
        assertEquals(2, day.unavailableSlots().size());
        assertEquals(6, day.standardSlots().size());
        assertEquals(2, day.remainderSlots().size());
        assertTrue(day.remainderSlots().stream().allMatch(slot -> slot.durationMinutes() == 30));
    }

    @Test
    @Transactional
    void fullDayBlockedRangeClosesTheWholeDayAndOverlapIsRejected() {
        long superAdminId = superAdminId();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(2);

        scheduleService.createException(
                superAdminId,
                date,
                LocalTime.of(10, 0),
                LocalTime.of(22, 0),
                "학교 행사"
        );

        var closed = scheduleService.day(superAdminId, date);
        assertEquals(ScheduleService.RoomStatus.CLOSED, closed.roomStatus());
        assertTrue(closed.standardSlots().isEmpty());
        assertTrue(closed.remainderSlots().isEmpty());
        assertEquals(1, closed.unavailableSlots().size());

        AppException error = assertThrows(AppException.class, () ->
                scheduleService.createException(
                        superAdminId,
                        date,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        "겹치는 예외"
                )
        );
        assertEquals("ROOM_EXCEPTION_OVERLAP", error.getCode());
    }

    @Test
    @Transactional
    void adminPolicyChangesAndBlockedPeriodsAreAudited() {
        long superAdminId = superAdminId();

        scheduleService.updateSettings(superAdminId, true, 1440, 60);

        var round = scheduleService.adminRounds(superAdminId).getFirst();
        scheduleService.updateRound(
                superAdminId,
                round.id(),
                round.bookingOpenAt().minusSeconds(1800),
                120
        );

        LocalDate exceptionDate = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(3);
        var exception = scheduleService.createException(
                superAdminId,
                exceptionDate,
                LocalTime.of(15, 0),
                LocalTime.of(16, 30),
                "테스트 예외"
        );
        scheduleService.deleteException(superAdminId, exception.id());

        var logs = actionLogService.list(superAdminId, 50);
        assertTrue(logs.stream().anyMatch(log -> "SCHEDULE_SETTINGS_UPDATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "BOOKING_ROUND_UPDATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ROOM_EXCEPTION_CREATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ROOM_EXCEPTION_DELETE".equals(log.actionType())));
    }

    private long superAdminId() {
        return userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();
    }
}
