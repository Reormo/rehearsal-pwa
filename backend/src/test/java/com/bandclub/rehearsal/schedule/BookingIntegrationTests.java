package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class BookingIntegrationTests {

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
    void sixtyMinuteBookingCanStartOnAnyThirtyMinuteBoundary() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate();
        var song = createLedSong(superAdminId, "30분 경계 예약 테스트");

        var dayBefore = scheduleService.day(superAdminId, date);
        assertEquals(12, dayBefore.standardSlots().size());

        var options = bookingService.options(superAdminId, date, 60);
        Instant tenThirty = at(date, 10, 30);
        assertTrue(options.acceptingReservations());
        assertTrue(options.options().stream().anyMatch(option ->
                option.startAt().equals(tenThirty)
                        && option.endAt().equals(at(date, 11, 30))
        ));

        var reservation = bookingService.create(
                superAdminId,
                song.id(),
                tenThirty,
                60
        );
        assertEquals(ReservationStatus.ACTIVE, reservation.status());
        assertEquals(song.id(), reservation.songId());

        var atomicSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        tenThirty,
                        at(date, 11, 30)
                );
        assertEquals(2, atomicSlots.size());
        assertTrue(atomicSlots.stream().allMatch(slot ->
                reservation.id().equals(slot.getReservationId())
        ));

        var dayAfter = scheduleService.day(superAdminId, date);
        assertTrue(dayAfter.unavailableSlots().stream().anyMatch(slot ->
                reservation.id().equals(slot.reservationId())
                        && song.id().equals(slot.songId())
                        && song.title().equals(slot.songTitle())
        ));
        assertTrue(bookingService.myUpcoming(superAdminId).stream().anyMatch(item ->
                reservation.id().equals(item.id())
        ));
    }

    @Test
    void multipleReservationsAreBlockedPerSongAndTargetRound() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(1);
        var song = createLedSong(superAdminId, "복수 예약 차단 테스트");

        bookingService.create(
                superAdminId,
                song.id(),
                at(date, 10, 0),
                60
        );

        AppException error = assertThrows(AppException.class, () ->
                bookingService.create(
                        superAdminId,
                        song.id(),
                        at(date, 13, 0),
                        60
                )
        );
        assertEquals("MULTIPLE_RESERVATIONS_NOT_ALLOWED", error.getCode());
    }

    @Test
    void concurrentRequestsForSameSlotsHaveExactlyOneWinner() throws Exception {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(2);
        Instant startAt = at(date, 13, 0);
        int requestCount = 20;

        var songs = new java.util.ArrayList<SongService.SongView>();
        for (int index = 0; index < requestCount; index++) {
            songs.add(createLedSong(superAdminId, "동시성 테스트 " + index));
        }

        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            var attempts = new java.util.ArrayList<Future<Boolean>>();
            for (var song : songs) {
                attempts.add(executor.submit(() -> attemptBooking(
                        superAdminId,
                        song.id(),
                        startAt,
                        ready,
                        start
                )));
            }

            assertTrue(
                    ready.await(10, java.util.concurrent.TimeUnit.SECONDS),
                    "20개 동시 예약 요청이 준비 상태에 도달해야 합니다."
            );
            start.countDown();

            int successCount = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(30, java.util.concurrent.TimeUnit.SECONDS)) {
                    successCount++;
                }
            }

            assertEquals(
                    1,
                    successCount,
                    "동일 슬롯 20건 동시 예약에서는 정확히 1건만 성공해야 합니다."
            );
        } finally {
            executor.shutdownNow();
        }

        var slots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        startAt,
                        startAt.plusSeconds(60 * 60L)
                );
        assertEquals(2, slots.size());
        assertNotNull(slots.getFirst().getReservationId());
        assertEquals(slots.getFirst().getReservationId(), slots.getLast().getReservationId());
    }

    @Test
    void blockedPeriodCancelsOnlyOverlappingReservationAndReleasesItsSlots() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(3);
        var song = createLedSong(superAdminId, "예외 시간 취소 테스트");
        Instant reservationStart = at(date, 15, 0);

        var reservation = bookingService.create(
                superAdminId,
                song.id(),
                reservationStart,
                60
        );

        scheduleService.createException(
                superAdminId,
                date,
                "15:30",
                "16:30",
                "장비 점검"
        );

        var canceled = reservationRepository.findById(reservation.id()).orElseThrow();
        assertEquals(ReservationStatus.CANCELED, canceled.getStatus());
        assertEquals(Long.valueOf(superAdminId), canceled.getCanceledBy());
        assertNotNull(canceled.getCanceledAt());

        var reservationSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.id(),
                        reservationStart,
                        reservationStart.plusSeconds(60 * 60L)
                );
        assertEquals(2, reservationSlots.size());
        assertTrue(reservationSlots.stream().allMatch(slot -> slot.getReservationId() == null));
        assertTrue(bookingService.myUpcoming(superAdminId).stream().noneMatch(item ->
                reservation.id().equals(item.id())
        ));
    }

    private boolean attemptBooking(
            long userId,
            long songId,
            Instant startAt,
            CountDownLatch ready,
            CountDownLatch start
    ) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            bookingService.create(userId, songId, startAt, 60);
            return true;
        } catch (AppException exception) {
            assertEquals("SLOT_ALREADY_RESERVED", exception.getCode());
            return false;
        }
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
        return songService.createSong(
                superAdminId,
                title,
                superAdminId,
                "기타"
        );
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
