package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.repository.NotificationRepository;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.SwapRequestStatus;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.SwapRequestRepository;
import com.bandclub.rehearsal.schedule.service.BookingService;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import com.bandclub.rehearsal.schedule.service.SwapService;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SwapIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    SwapService swapService;

    @Autowired
    BookingService bookingService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    SwapRequestRepository swapRequestRepository;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void leaderAcceptSwapsAtomicallyAndPreservesEachDuration() {
        long userId = superAdminId();
        var round = openNextRound(userId, 180);
        LocalDate date = round.startDate().plusDays(1);
        var firstSong = songService.createSong(userId, "교환 길이 A", userId, "기타");
        var secondSong = songService.createSong(userId, "교환 길이 B", userId, "드럼");
        var first = bookingService.create(userId, firstSong.id(), at(date, 12, 0), 60);
        var second = bookingService.create(userId, secondSong.id(), at(date, 15, 0), 90);

        var request = swapService.request(userId, first.id(), second.id());
        var accepted = swapService.accept(userId, request.id());

        assertEquals(SwapRequestStatus.ACCEPTED, accepted.status());
        var firstAfter = reservationRepository.findById(first.id()).orElseThrow();
        var secondAfter = reservationRepository.findById(second.id()).orElseThrow();
        assertEquals(at(date, 15, 0), firstAfter.getStartAt());
        assertEquals(60, Duration.between(firstAfter.getStartAt(), firstAfter.getEndAt()).toMinutes());
        assertEquals(at(date, 12, 0), secondAfter.getStartAt());
        assertEquals(90, Duration.between(secondAfter.getStartAt(), secondAfter.getEndAt()).toMinutes());
    }

    @Test
    void oneReservationCannotParticipateInTwoPendingRequests() {
        long userId = superAdminId();
        var round = openNextRound(userId, 90);
        LocalDate date = round.startDate().plusDays(2);
        var songA = songService.createSong(userId, "중복 교환 A", userId, "기타");
        var songB = songService.createSong(userId, "중복 교환 B", userId, "베이스");
        var songC = songService.createSong(userId, "중복 교환 C", userId, "드럼");
        var a = bookingService.create(userId, songA.id(), at(date, 12, 0), 60);
        var b = bookingService.create(userId, songB.id(), at(date, 14, 0), 60);
        var c = bookingService.create(userId, songC.id(), at(date, 16, 0), 60);

        swapService.request(userId, a.id(), b.id());
        AppException error = assertThrows(AppException.class, () ->
                swapService.request(userId, a.id(), c.id())
        );
        assertEquals("SWAP_RESERVATION_ALREADY_PENDING", error.getCode());
    }

    @Test
    void reservationEditExpiresPendingSwapAndNotifiesOtherLeader() {
        long userId = superAdminId();
        var round = openNextRound(userId, 90);
        LocalDate date = round.startDate().plusDays(3);
        var songA = songService.createSong(userId, "만료 교환 A", userId, "기타");
        var songB = songService.createSong(userId, "만료 교환 B", userId, "드럼");
        var a = bookingService.create(userId, songA.id(), at(date, 11, 0), 60);
        var b = bookingService.create(userId, songB.id(), at(date, 14, 0), 60);
        var request = swapService.request(userId, a.id(), b.id());

        bookingService.move(userId, a.id(), at(date, 12, 0));

        assertEquals(
                SwapRequestStatus.EXPIRED,
                swapRequestRepository.findById(request.id()).orElseThrow().getStatus()
        );
        assertTrue(notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .anyMatch(notification -> notification.getType().equals("SWAP_EXPIRED")));
    }

    @Test
    void requesterCanCancelPendingSwap() {
        long userId = superAdminId();
        var round = openNextRound(userId, 60);
        LocalDate date = round.startDate().plusDays(4);
        var songA = songService.createSong(userId, "취소 교환 A", userId, "기타");
        var songB = songService.createSong(userId, "취소 교환 B", userId, "드럼");
        var a = bookingService.create(userId, songA.id(), at(date, 13, 0), 60);
        var b = bookingService.create(userId, songB.id(), at(date, 15, 0), 60);
        var request = swapService.request(userId, a.id(), b.id());

        var canceled = swapService.cancel(userId, request.id());
        assertEquals(SwapRequestStatus.CANCELED, canceled.status());
    }

    @Test
    void adminDirectSwapCreatesAcceptedHistory() {
        long userId = superAdminId();
        var round = openNextRound(userId, 90);
        LocalDate date = round.startDate().plusDays(5);
        var songA = songService.createSong(userId, "관리자 직접 교환 A", userId, "기타");
        var songB = songService.createSong(userId, "관리자 직접 교환 B", userId, "드럼");
        var a = bookingService.create(userId, songA.id(), at(date, 11, 0), 60);
        var b = bookingService.create(userId, songB.id(), at(date, 16, 0), 60);

        var result = swapService.adminDirect(userId, a.id(), b.id(), "관리자 직접 교환 테스트");
        assertEquals(SwapRequestStatus.ACCEPTED, result.status());
        assertEquals(ReservationStatus.ACTIVE, reservationRepository.findById(a.id()).orElseThrow().getStatus());
        assertEquals(at(date, 16, 0), reservationRepository.findById(a.id()).orElseThrow().getStartAt());
        assertEquals(at(date, 11, 0), reservationRepository.findById(b.id()).orElseThrow().getStartAt());
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
