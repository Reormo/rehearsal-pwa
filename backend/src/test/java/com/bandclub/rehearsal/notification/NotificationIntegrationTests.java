package com.bandclub.rehearsal.notification;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.notification.service.NotificationService;
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
class NotificationIntegrationTests {

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
    NotificationService notificationService;

    @Autowired
    UserRepository userRepository;

    @Test
    void canceledReservationShowsUnreadBadgeThenStaysUntilDismissed() {
        long superAdminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var nextRound = scheduleService.adminRounds(superAdminId).stream()
                .filter(round -> round.startDate().isAfter(today))
                .findFirst()
                .orElseThrow();
        var round = scheduleService.updateRound(
                superAdminId,
                nextRound.id(),
                Instant.now().minusSeconds(60),
                60
        );
        LocalDate date = round.startDate();
        var song = songService.createSong(
                superAdminId,
                "취소 알림 테스트",
                superAdminId,
                "기타"
        );

        bookingService.create(
                superAdminId,
                song.id(),
                at(date, 15, 0),
                60
        );
        scheduleService.createException(
                superAdminId,
                date,
                "15:30",
                "16:30",
                "장비 점검"
        );

        assertEquals(1, notificationService.unreadCount(superAdminId));

        var notification = notificationService.list(superAdminId).stream()
                .filter(item -> "RESERVATION_CANCELED".equals(item.type()))
                .findFirst()
                .orElseThrow();
        assertTrue(notification.title().contains("취소"));
        assertTrue(notification.body().contains(song.title()));
        assertTrue(notification.body().contains("장비 점검"));

        notificationService.markAllRead(superAdminId);
        assertEquals(0, notificationService.unreadCount(superAdminId));
        assertTrue(notificationService.list(superAdminId).stream().anyMatch(item ->
                notification.id().equals(item.id())
        ));

        notificationService.dismiss(superAdminId, notification.id());
        assertTrue(notificationService.list(superAdminId).stream().noneMatch(item ->
                notification.id().equals(item.id())
        ));
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
