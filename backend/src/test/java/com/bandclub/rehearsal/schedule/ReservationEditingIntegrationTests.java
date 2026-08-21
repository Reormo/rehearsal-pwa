package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.service.BookingService;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
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
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ReservationEditingIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    BookingService bookingService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    ReservationSlotRepository slotRepository;

    @Test
    void leaderCanMoveReservationWithinSameRoundAndKeepDuration() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate fromDate = round.startDate();
        LocalDate toDate = round.startDate().plusDays(1);
        var song = createLedSong(superAdminId, "예약 이동 테스트");

        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(fromDate, 13, 0),
                60
        );
        var moved = bookingService.move(
                superAdminId,
                created.id(),
                at(toDate, 16, 30)
        );

        assertEquals(at(toDate, 16, 30), moved.startAt());
        assertEquals(at(toDate, 17, 30), moved.endAt());
        assertEquals(60, java.time.Duration.between(
                moved.startAt(),
                moved.endAt()
        ).toMinutes());

        var oldSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        at(fromDate, 13, 0),
                        at(fromDate, 14, 0)
                );
        assertTrue(oldSlots.stream().allMatch(slot -> slot.getReservationId() == null));

        var newSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        at(toDate, 16, 30),
                        at(toDate, 17, 30)
                );
        assertTrue(newSlots.stream().allMatch(slot ->
                created.id().equals(slot.getReservationId())));
    }

    @Test
    void moveToAnotherRoundIsRejectedWithoutChangingOriginalReservation() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        var song = createLedSong(superAdminId, "회차 이동 차단 테스트");
        Instant originalStart = at(round.startDate(), 14, 0);

        var created = bookingService.create(
                superAdminId,
                song.id(),
                originalStart,
                60
        );

        AppException error = assertThrows(AppException.class, () ->
                bookingService.move(
                        superAdminId,
                        created.id(),
                        at(round.endDate().plusDays(1), 14, 0)
                )
        );
        assertEquals("RESERVATION_MOVE_OUTSIDE_ROUND", error.getCode());

        var unchanged = reservationRepository.findById(created.id()).orElseThrow();
        assertEquals(originalStart, unchanged.getStartAt());
        assertEquals(originalStart.plusSeconds(60 * 60L), unchanged.getEndAt());
    }

    @Test
    void leaderCanExtendAndShortenFrontOrBackByThirtyMinutes() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate date = round.startDate().plusDays(2);
        var song = createLedSong(superAdminId, "예약 길이 수정 테스트");

        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(date, 13, 0),
                60
        );
        var extended = bookingService.extend(
                superAdminId,
                created.id(),
                ReservationBoundary.FRONT
        );
        assertEquals(at(date, 12, 30), extended.startAt());
        assertEquals(at(date, 14, 0), extended.endAt());

        var shortened = bookingService.shorten(
                superAdminId,
                created.id(),
                ReservationBoundary.BACK
        );
        assertEquals(at(date, 12, 30), shortened.startAt());
        assertEquals(at(date, 13, 30), shortened.endAt());
    }

    @Test
    void leaderCancelHasNoReasonAndReleasesSlots() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(3);
        var song = createLedSong(superAdminId, "예약 취소 테스트");
        Instant startAt = at(date, 15, 0);

        var created = bookingService.create(
                superAdminId,
                song.id(),
                startAt,
                60
        );
        bookingService.cancel(superAdminId, created.id());

        var canceled = reservationRepository.findById(created.id()).orElseThrow();
        assertEquals(ReservationStatus.CANCELED, canceled.getStatus());
        assertEquals(Long.valueOf(superAdminId), canceled.getCanceledBy());
        assertNull(canceled.getCancellationReason());
        assertNotNull(canceled.getCanceledAt());

        var slots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        startAt,
                        startAt.plusSeconds(60 * 60L)
                );
        assertTrue(slots.stream().allMatch(slot -> slot.getReservationId() == null));
    }

    private ScheduleService.RoundView openNextRound(long superAdminId, int maxMinutes) {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var nextRound = scheduleService.adminRounds(superAdminId).stream()
                .filter(round -> round.startDate().isAfter(today))
                .findFirst()
                .orElseThrow();
        return scheduleService.updateRound(
                superAdminId,
                nextRound.id(),
                Instant.now().minusSeconds(60),
                maxMinutes
        );
    }

    private SongService.SongView createLedSong(long superAdminId, String title) {
        return songService.createSong(superAdminId, title, superAdminId, "기타");
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
