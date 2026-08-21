package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.notification.service.NotificationService;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.service.AdminReservationService;
import com.bandclub.rehearsal.schedule.service.AdminRoomOperatingHoursService;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class ReservationOperationNotificationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    BookingService bookingService;

    @Autowired
    AdminReservationService adminReservationService;

    @Autowired
    AdminRoomOperatingHoursService operatingHoursService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    NotificationService notificationService;

    @Autowired
    UserRepository userRepository;

    @Test
    void teamAndAdminTimeChangesNotifyTheSongMemberIncludingActor() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 90);
        LocalDate date = round.startDate().plusDays(1);
        var song = songService.createSong(
                superAdminId,
                "예약 변경 알림 테스트",
                superAdminId,
                "기타"
        );

        var created = bookingService.create(
                superAdminId,
                song.id(),
                at(date, 13, 0),
                60
        );
        notificationService.markAllRead(superAdminId);

        bookingService.move(
                superAdminId,
                created.id(),
                at(date, 14, 0)
        );

        assertEquals(1, notificationService.unreadCount(superAdminId));
        var teamChange = notificationService.list(superAdminId).stream()
                .filter(item -> "RESERVATION_CHANGED".equals(item.type()))
                .findFirst()
                .orElseThrow();
        assertTrue(teamChange.title().contains("변경"));
        assertTrue(teamChange.body().contains(song.title()));
        assertTrue(teamChange.body().contains("→"));

        notificationService.markAllRead(superAdminId);
        adminReservationService.extend(
                superAdminId,
                created.id(),
                ReservationBoundary.BACK,
                "운영진 요청으로 30분 연장"
        );

        assertEquals(1, notificationService.unreadCount(superAdminId));
        assertTrue(notificationService.list(superAdminId).stream().anyMatch(item ->
                "RESERVATION_CHANGED".equals(item.type())
                        && item.body().contains(song.title())
        ));

        notificationService.markAllRead(superAdminId);
        adminReservationService.cancel(
                superAdminId,
                created.id(),
                "운영진 강제 취소 테스트"
        );
        assertEquals(1, notificationService.unreadCount(superAdminId));
        assertTrue(notificationService.list(superAdminId).stream().anyMatch(item ->
                "RESERVATION_CANCELED".equals(item.type())
                        && item.body().contains("운영진 강제 취소 테스트")
        ));
    }

    @Test
    void operatingHoursRestoreCancellationCreatesCancellationNotification() {
        long superAdminId = superAdminId();
        var round = openNextRound(superAdminId, 60);
        LocalDate date = round.startDate().plusDays(2);
        var song = songService.createSong(
                superAdminId,
                "운영시간 자동 취소 알림 테스트",
                superAdminId,
                "기타"
        );

        operatingHoursService.override(
                superAdminId,
                date,
                "08:00",
                "24:00",
                "오전 운영 확대"
        );
        bookingService.create(
                superAdminId,
                song.id(),
                at(date, 9, 0),
                30
        );
        notificationService.markAllRead(superAdminId);

        operatingHoursService.restoreDefault(
                superAdminId,
                date,
                "기본 운영시간 복원"
        );

        assertEquals(1, notificationService.unreadCount(superAdminId));
        assertTrue(notificationService.list(superAdminId).stream().anyMatch(item ->
                "RESERVATION_CANCELED".equals(item.type())
                        && item.body().contains(song.title())
                        && item.body().contains("기본 운영시간 복원")
        ));
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
