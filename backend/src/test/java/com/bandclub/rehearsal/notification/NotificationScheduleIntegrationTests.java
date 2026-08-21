package com.bandclub.rehearsal.notification;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.notification.repository.NotificationRepository;
import com.bandclub.rehearsal.notification.service.NotificationScheduleService;
import com.bandclub.rehearsal.notification.service.WebPushService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class NotificationScheduleIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    NotificationRepository notificationRepository;

    @Autowired
    UserRepository userRepository;

    @Test
    void bookingOpenNotificationsAreIdempotent() {
        long userId = superAdminId();
        long clubId = clubId(userId);
        Instant now = Instant.parse("2030-01-05T11:50:00Z");

        insertRound(
                clubId,
                9001,
                LocalDate.of(2030, 1, 7),
                now.plusSeconds(600),
                now.plusSeconds(60 * 60 * 24 * 8)
        );

        NotificationScheduleService service =
                new NotificationScheduleService(
                        jdbcTemplate,
                        mock(WebPushService.class)
                );

        var first = service.process(now);
        var second = service.process(now.plusSeconds(20));

        assertEquals(1, first.bookingPreOpenCreated());
        assertEquals(0, second.bookingPreOpenCreated());

        long count = notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .filter(item -> item.getType().equals("BOOKING_OPEN_10_MIN"))
                .count();

        assertEquals(1, count);
    }

    @Test
    void rehearsalReminderIsCreatedOnlyOnceForCurrentReservationStart() {
        long userId = superAdminId();
        long clubId = clubId(userId);
        Instant now = Instant.parse("2030-02-01T08:00:00Z");
        LocalDate date = LocalDate.of(2030, 2, 4);

        long roundId = insertRound(
                clubId,
                9002,
                date,
                now.minusSeconds(3600),
                now.plusSeconds(60 * 60 * 24 * 10)
        );
        long songId = insertSong(clubId, userId, now);
        insertSongMember(songId, userId, now);

        jdbcTemplate.update(
                """
                update user_notification_settings
                set rehearsal_reminder_minutes = 30,
                    updated_at = ?
                where user_id = ?
                """,
                Timestamp.from(now),
                userId
        );

        Instant startAt = now.plusSeconds(30 * 60);
        insertReservation(
                roundId,
                songId,
                userId,
                startAt,
                startAt.plusSeconds(60 * 60),
                now
        );

        NotificationScheduleService service =
                new NotificationScheduleService(
                        jdbcTemplate,
                        mock(WebPushService.class)
                );

        var first = service.process(now);
        var second = service.process(now.plusSeconds(10));

        assertEquals(1, first.rehearsalRemindersCreated());
        assertEquals(0, second.rehearsalRemindersCreated());

        long count = notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .filter(item -> item.getType().equals("REHEARSAL_REMINDER"))
                .count();

        assertEquals(1, count);
    }

    @Test
    void newAnnouncementCreatesInternalNotificationForClubMember() {
        long userId = superAdminId();
        long clubId = clubId(userId);
        Instant now = Instant.parse("2030-03-01T00:00:00Z");

        jdbcTemplate.update(
                """
                insert into announcements (
                    club_id,
                    title,
                    content,
                    is_pinned,
                    author_user_id,
                    created_at,
                    updated_at
                )
                values (?, '알림 테스트 공지', '본문', false, ?, ?, ?)
                """,
                clubId,
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
        );

        long count = notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .filter(item -> item.getType().equals("ANNOUNCEMENT"))
                .filter(item -> item.getBody().equals("알림 테스트 공지"))
                .count();

        assertEquals(1, count);
    }

    private long insertRound(
            long clubId,
            int roundNo,
            LocalDate startDate,
            Instant bookingOpenAt,
            Instant bookingCloseAt
    ) {
        return jdbcTemplate.queryForObject(
                """
                insert into booking_rounds (
                    club_id,
                    round_no,
                    start_date,
                    end_date,
                    booking_open_at,
                    booking_close_at,
                    max_reservation_minutes,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, 90, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                roundNo,
                Date.valueOf(startDate),
                Date.valueOf(startDate.plusDays(6)),
                Timestamp.from(bookingOpenAt),
                Timestamp.from(bookingCloseAt),
                Timestamp.from(bookingOpenAt.minusSeconds(3600)),
                Timestamp.from(bookingOpenAt.minusSeconds(3600))
        );
    }

    private long insertSong(long clubId, long userId, Instant now) {
        return jdbcTemplate.queryForObject(
                """
                insert into songs (
                    club_id,
                    title,
                    status,
                    archived_at,
                    created_by,
                    created_at,
                    updated_at
                )
                values (?, '알림 테스트 곡', 'ACTIVE', null, ?, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private void insertSongMember(
            long songId,
            long userId,
            Instant now
    ) {
        jdbcTemplate.update(
                """
                insert into song_members (
                    song_id,
                    user_id,
                    session_name,
                    is_leader,
                    created_at,
                    updated_at
                )
                values (?, ?, '기타', true, ?, ?)
                """,
                songId,
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private long insertReservation(
            long roundId,
            long songId,
            long userId,
            Instant startAt,
            Instant endAt,
            Instant now
    ) {
        return jdbcTemplate.queryForObject(
                """
                insert into reservations (
                    booking_round_id,
                    song_id,
                    start_at,
                    end_at,
                    status,
                    source,
                    created_by,
                    canceled_by,
                    cancellation_reason,
                    canceled_at,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, 'ACTIVE', 'TEAM', ?, null, null, null, ?, ?)
                returning id
                """,
                Long.class,
                roundId,
                songId,
                Timestamp.from(startAt),
                Timestamp.from(endAt),
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private long clubId(long userId) {
        return jdbcTemplate.queryForObject(
                "select club_id from club_members where user_id = ?",
                Long.class,
                userId
        );
    }

    private long superAdminId() {
        return userRepository.findAll().stream()
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
