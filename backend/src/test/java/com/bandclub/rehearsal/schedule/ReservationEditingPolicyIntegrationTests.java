package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.service.AdminReservationService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ReservationEditingPolicyIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    BookingService bookingService;

    @Autowired
    AdminReservationService adminReservationService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void startedReservationCannotBeCanceledByLeader() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        var song = songService.createSong(
                superAdminId,
                "시작 후 수정 차단 테스트",
                superAdminId,
                "기타"
        );
        Instant now = Instant.now();
        Reservation started = reservationRepository.saveAndFlush(Reservation.team(
                round.id(),
                song.id(),
                now.minusSeconds(60),
                now.plusSeconds(29 * 60L),
                superAdminId,
                now.minusSeconds(120)
        ));

        AppException error = assertThrows(AppException.class, () ->
                bookingService.cancel(superAdminId, started.getId())
        );
        assertEquals("RESERVATION_ALREADY_STARTED", error.getCode());
    }

    @Test
    void archivedReservationIsLeaderLockedButAdminCanForceMoveIt() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate date = round.startDate().plusDays(1);
        var song = songService.createSong(
                superAdminId,
                "보관 곡 예약 수정 테스트",
                superAdminId,
                "기타"
        );
        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(date, 13, 0),
                60
        );
        songService.archiveSong(superAdminId, song.id());

        AppException leaderError = assertThrows(AppException.class, () ->
                bookingService.move(superAdminId, created.id(), at(date, 15, 0))
        );
        assertEquals("SONG_ARCHIVED", leaderError.getCode());

        var moved = adminReservationService.move(
                superAdminId,
                created.id(),
                at(date, 15, 0),
                "보관 곡 관리자 강제 이동"
        );
        assertEquals(at(date, 15, 0), moved.startAt());
    }

    @Test
    void reducedMaxKeepsExistingDurationForMoveButBlocksExtension() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate date = round.startDate().plusDays(2);
        var song = songService.createSong(
                superAdminId,
                "최대 길이 축소 테스트",
                superAdminId,
                "기타"
        );
        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(date, 12, 0),
                90
        );

        scheduleService.updateRound(
                superAdminId,
                round.id(),
                round.bookingOpenAt(),
                30
        );

        var moved = bookingService.move(
                superAdminId,
                created.id(),
                at(date, 15, 0)
        );
        assertEquals(90, java.time.Duration.between(
                moved.startAt(),
                moved.endAt()
        ).toMinutes());

        AppException error = assertThrows(AppException.class, () ->
                bookingService.extend(
                        superAdminId,
                        created.id(),
                        ReservationBoundary.BACK
                )
        );
        assertEquals("RESERVATION_TOO_LONG", error.getCode());
    }

    @Test
    void adminUpcomingListsReservationsAcrossClubSongs() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(4);
        var song = songService.createSong(
                superAdminId,
                "관리자 예약 목록 테스트",
                superAdminId,
                "기타"
        );
        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(date, 16, 0),
                60
        );

        assertEquals(1, adminReservationService.upcoming(superAdminId).stream()
                .filter(item -> item.id().equals(created.id()))
                .count());
    }

    @Test
    void moveStillRejectsAnotherReservationCollision() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate date = round.startDate().plusDays(3);
        var firstSong = songService.createSong(superAdminId, "충돌 A", superAdminId, "기타");
        var secondSong = songService.createSong(superAdminId, "충돌 B", superAdminId, "기타");

        var first = bookingService.create(
                superAdminId,
                firstSong.id(),
                at(date, 13, 0),
                60
        );
        bookingService.create(
                superAdminId,
                secondSong.id(),
                at(date, 15, 0),
                60
        );

        AppException error = assertThrows(AppException.class, () ->
                bookingService.move(superAdminId, first.id(), at(date, 15, 0))
        );
        assertEquals("SLOT_ALREADY_RESERVED", error.getCode());
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
