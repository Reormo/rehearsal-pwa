package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.RoomOperatingHours;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomOperatingHoursRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    BookingRoundRepository roundRepository;

    @Autowired
    RoomOperatingHoursRepository operatingHoursRepository;

    @Autowired
    SongService songService;

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
        assertEquals(48, atomicSlots.size());

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

        var testSong = songService.createSong(
                superAdminId,
                "슬롯 계산 테스트",
                superAdminId,
                "기타"
        );
        Reservation sixtyMinuteReservation = reservationRepository.saveAndFlush(Reservation.team(
                round.id(),
                testSong.id(),
                atomicSlots.get(20).getSlotStartAt(),
                atomicSlots.get(20).getSlotStartAt().plusSeconds(60 * 60L),
                superAdminId,
                Instant.now()
        ));
        atomicSlots.get(20).occupy(sixtyMinuteReservation.getId());
        atomicSlots.get(21).occupy(sixtyMinuteReservation.getId());
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

        atomicSlots.get(20).release();
        atomicSlots.get(21).release();
        Reservation thirtyMinuteReservation = reservationRepository.saveAndFlush(Reservation.team(
                round.id(),
                testSong.id(),
                atomicSlots.get(22).getSlotStartAt(),
                atomicSlots.get(22).getSlotStartAt().plusSeconds(30 * 60L),
                superAdminId,
                Instant.now()
        ));
        atomicSlots.get(22).occupy(thirtyMinuteReservation.getId());
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
                "13:00",
                "14:00",
                "장비 점검"
        );
        scheduleService.createException(
                superAdminId,
                date,
                "16:00",
                "17:00",
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
                "10:00",
                "22:00",
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
                        "13:00",
                        "14:00",
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
                "15:00",
                "16:30",
                "테스트 예외"
        );
        scheduleService.deleteException(superAdminId, exception.id());

        var logs = actionLogService.list(superAdminId, 50);
        assertTrue(logs.stream().anyMatch(log -> "SCHEDULE_SETTINGS_UPDATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "BOOKING_ROUND_UPDATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ROOM_EXCEPTION_CREATE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log -> "ROOM_EXCEPTION_DELETE".equals(log.actionType())));
    }

    @Test
    @Transactional
    void operatingHoursOverrideSupportsTwentyFourHourBoundary() {
        long superAdminId = superAdminId();
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1);
        var round = scheduleService.adminRounds(superAdminId).stream()
                .filter(candidate ->
                        !date.isBefore(candidate.startDate())
                                && !date.isAfter(candidate.endDate()))
                .findFirst()
                .orElseThrow();
        Long clubId = roundRepository.findById(round.id())
                .orElseThrow()
                .getClubId();

        operatingHoursRepository.save(RoomOperatingHours.create(
                clubId,
                date,
                480,
                1440,
                "공연 준비",
                superAdminId,
                Instant.now()
        ));

        var day = scheduleService.day(superAdminId, date);
        assertEquals(480, day.operatingHours().openMinute());
        assertEquals(1440, day.operatingHours().closeMinute());
        assertTrue(day.operatingHours().overridden());
        assertEquals(
                date.atTime(8, 0).atZone(ScheduleService.SERVICE_ZONE).toInstant(),
                day.standardSlots().getFirst().startAt()
        );
        assertEquals(
                date.plusDays(1).atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant(),
                day.remainderSlots().getLast().endAt()
        );
    }

    private long superAdminId() {
        return userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();
    }
}
