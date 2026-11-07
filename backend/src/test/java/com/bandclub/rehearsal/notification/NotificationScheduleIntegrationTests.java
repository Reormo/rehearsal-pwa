package com.bandclub.rehearsal.notification;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.notification.repository.NotificationRepository;
import com.bandclub.rehearsal.notification.service.NotificationScheduleService;
import com.bandclub.rehearsal.notification.service.WebPushService;
import com.bandclub.rehearsal.schedule.service.StageBookingWindowService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
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

    @Autowired
    StageBookingWindowService stageBookingWindowService;

    @Autowired
    EntityManager entityManager;

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
        long songId = insertSong(clubId, userId, now);
        insertSongMember(songId, userId, now);

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
    void defaultAndCustomStageOpeningAlertsOnlyReachTheirOwnLeaders() {
        long festivalLeaderId = superAdminId();
        long clubId = clubId(festivalLeaderId);
        Instant now = Instant.parse("2030-04-05T11:50:00Z");

        long buskingLeaderId = insertActiveMember(clubId, "busking_alert_leader");
        long ordinaryMemberId = insertActiveMember(clubId, "unrelated_alert_member");
        long festivalStageId = insertStageType(clubId, "축제", now);
        long buskingStageId = insertStageType(clubId, "버스킹", now);

        Instant defaultOpenAt = now.plusSeconds(600);
        Instant customOpenAt = now.plusSeconds(4200);
        Instant closeAt = Instant.parse("2030-04-14T12:00:00Z");
        long roundId = insertRound(
                clubId,
                9011,
                LocalDate.of(2030, 4, 8),
                defaultOpenAt,
                closeAt
        );
        // Exercise the real admin save service instead of direct SQL.
        var savedWindow = stageBookingWindowService.upsert(
                festivalLeaderId, roundId, buskingStageId,
                customOpenAt, closeAt, 90, 1
        );
        entityManager.flush();
        assertEquals(customOpenAt, savedWindow.bookingOpenAt());
        assertEquals(buskingStageId, savedWindow.stageTypeId());
        assertEquals(customOpenAt, jdbcTemplate.queryForObject(
                """
                select booking_open_at from booking_round_stage_windows
                where booking_round_id = ? and stage_type_id = ?
                """,
                Timestamp.class, roundId, buskingStageId
        ).toInstant());

        long festivalSong = insertSongForStage(
                clubId, festivalLeaderId, festivalStageId, "축제 알림 테스트곡", now
        );
        long buskingSong = insertSongForStage(
                clubId, festivalLeaderId, buskingStageId, "버스킹 알림 테스트곡", now
        );
        insertSongMember(festivalSong, festivalLeaderId, now);
        insertSongMember(buskingSong, buskingLeaderId, now);
        insertSongMember(festivalSong, ordinaryMemberId, now, false);
        insertSongMember(buskingSong, ordinaryMemberId, now, false);

        WebPushService push = mock(WebPushService.class);
        NotificationScheduleService service =
                new NotificationScheduleService(jdbcTemplate, push);

        // Default-stage alert: only the Festival song leader is notified.
        var festivalPre = service.process(now);
        assertEquals(1, festivalPre.bookingPreOpenCreated());
        assertEquals(0, festivalPre.bookingOpenCreated());
        assertEquals(1, bookingAlertCount(roundId, festivalLeaderId, "BOOKING_OPEN_10_MIN"));
        assertEquals(0, bookingAlertCount(roundId, buskingLeaderId, "BOOKING_OPEN_10_MIN"));
        assertEquals(0, bookingAlertCount(roundId, ordinaryMemberId, "BOOKING_OPEN_10_MIN"));
        assertEquals(0, service.process(now.plusSeconds(20)).bookingPreOpenCreated());

        var festivalOpen = service.process(defaultOpenAt);
        assertEquals(1, festivalOpen.bookingOpenCreated());
        assertEquals(1, bookingAlertCount(roundId, festivalLeaderId, "BOOKING_OPEN"));
        assertEquals(0, bookingAlertCount(roundId, buskingLeaderId, "BOOKING_OPEN"));
        assertEquals(0, bookingAlertCount(roundId, ordinaryMemberId, "BOOKING_OPEN"));

        // Custom-stage alert occurs at its own time, not at the default time.
        var buskingPre = service.process(customOpenAt.minusSeconds(600));
        assertEquals(1, buskingPre.bookingPreOpenCreated());
        assertEquals(1, bookingAlertCount(roundId, buskingLeaderId, "BOOKING_OPEN_10_MIN"));
        assertEquals(1, bookingAlertCount(roundId, festivalLeaderId, "BOOKING_OPEN_10_MIN"));
        assertEquals(0, bookingAlertCount(roundId, ordinaryMemberId, "BOOKING_OPEN_10_MIN"));
        assertEquals(0, service.process(customOpenAt.minusSeconds(580)).bookingPreOpenCreated());

        var buskingOpen = service.process(customOpenAt);
        assertEquals(1, buskingOpen.bookingOpenCreated());
        assertEquals(1, bookingAlertCount(roundId, buskingLeaderId, "BOOKING_OPEN"));
        assertEquals(1, bookingAlertCount(roundId, festivalLeaderId, "BOOKING_OPEN"));
        assertEquals(0, bookingAlertCount(roundId, ordinaryMemberId, "BOOKING_OPEN"));
        assertEquals(0, service.process(customOpenAt.plusSeconds(20)).bookingOpenCreated());

        // A stage's leader receives only its stage-specific push payloads.
        org.mockito.Mockito.verify(push, org.mockito.Mockito.times(2))
                .sendTimeSensitiveToUser(
                        org.mockito.ArgumentMatchers.eq(festivalLeaderId),
                        org.mockito.ArgumentMatchers.contains("축제"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("/schedule"),
                        org.mockito.ArgumentMatchers.anyString()
                );
        org.mockito.Mockito.verify(push, org.mockito.Mockito.times(2))
                .sendTimeSensitiveToUser(
                        org.mockito.ArgumentMatchers.eq(buskingLeaderId),
                        org.mockito.ArgumentMatchers.contains("버스킹"),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("/schedule"),
                        org.mockito.ArgumentMatchers.anyString()
                );
        org.mockito.Mockito.verify(push, org.mockito.Mockito.never())
                .sendTimeSensitiveToUser(
                        org.mockito.ArgumentMatchers.eq(ordinaryMemberId),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString()
                );
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
    void stageSpecificBookingWindowCanNotifyAgainAfterOpenTimeChanges() {
        long userId = superAdminId();
        long clubId = clubId(userId);
        Instant now = Instant.parse("2030-01-12T10:00:00Z");

        long roundId = insertRound(
                clubId,
                9003,
                LocalDate.of(2030, 1, 14),
                now.plusSeconds(600),
                now.plusSeconds(60 * 60 * 24 * 8)
        );
        long songId = insertSong(clubId, userId, now);
        insertSongMember(songId, userId, now);

        NotificationScheduleService service =
                new NotificationScheduleService(
                        jdbcTemplate,
                        mock(WebPushService.class)
                );

        assertEquals(1, service.process(now).bookingPreOpenCreated());

        long stageTypeId = jdbcTemplate.queryForObject(
                "select stage_type_id from songs where id = ?",
                Long.class,
                songId
        );
        Instant customOpenAt = now.plusSeconds(60 * 60);
        insertStageWindow(
                roundId,
                stageTypeId,
                userId,
                customOpenAt,
                now.plusSeconds(60 * 60 * 24 * 8),
                now
        );

        assertEquals(
                1,
                service.process(customOpenAt.minusSeconds(600))
                        .bookingPreOpenCreated()
        );
    }

    @Test
    void rehearsalReminderIsSkippedWhenItsConfiguredTimeIsAlreadyTooOld() {
        long userId = superAdminId();
        long clubId = clubId(userId);
        Instant now = Instant.parse("2030-02-08T08:00:00Z");
        LocalDate date = LocalDate.of(2030, 2, 11);

        long roundId = insertRound(
                clubId,
                9004,
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

        Instant startAt = now.plusSeconds(10 * 60);
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

        assertEquals(0, service.process(now).rehearsalRemindersCreated());

        long count = notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .filter(item -> item.getType().equals("REHEARSAL_REMINDER"))
                .count();

        assertEquals(0, count);
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

    private long bookingAlertCount(long roundId, long userId, String type) {
        String prefix = type.equals("BOOKING_OPEN_10_MIN")
                ? "booking-open-10:"
                : "booking-open:";
        return jdbcTemplate.queryForObject(
                """
                select count(*)
                from notifications
                where user_id = ?
                  and type = ?
                  and dedupe_key like ?
                """,
                Long.class,
                userId,
                type,
                prefix + roundId + ":%"
        );
    }

    private long insertActiveMember(long clubId, String loginId) {
        long userId = jdbcTemplate.queryForObject(
                """
                insert into users (login_id, name, status)
                values (?, ?, 'ACTIVE')
                returning id
                """,
                Long.class,
                loginId,
                loginId
        );
        jdbcTemplate.update(
                """
                insert into club_members (club_id, user_id, role)
                values (?, ?, 'MEMBER')
                """,
                clubId,
                userId
        );
        return userId;
    }

    private long insertStageType(long clubId, String name, Instant now) {
        return jdbcTemplate.queryForObject(
                """
                insert into stage_types (
                    club_id, name, normalized_name, created_at, updated_at
                )
                values (?, ?, ?, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                name,
                name,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }

    private long insertSongForStage(
            long clubId,
            long createdBy,
            long stageTypeId,
            String title,
            Instant now
    ) {
        return jdbcTemplate.queryForObject(
                """
                insert into songs (
                    club_id, title, stage_type_id, status,
                    archived_at, created_by, created_at, updated_at
                )
                values (?, ?, ?, 'ACTIVE', null, ?, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                title,
                stageTypeId,
                createdBy,
                Timestamp.from(now),
                Timestamp.from(now)
        );
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

    private void insertStageWindow(
            long roundId,
            long stageTypeId,
            long userId,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            Instant now
    ) {
        jdbcTemplate.update(
                """
                insert into booking_round_stage_windows (
                    booking_round_id,
                    stage_type_id,
                    booking_open_at,
                    booking_close_at,
                    max_reservation_minutes,
                    max_reservations_per_song,
                    updated_by,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, 90, 1, ?, ?, ?)
                """,
                roundId,
                stageTypeId,
                Timestamp.from(bookingOpenAt),
                Timestamp.from(bookingCloseAt),
                userId,
                Timestamp.from(now),
                Timestamp.from(now)
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

    private void insertSongMember(long songId, long userId, Instant now) {
        insertSongMember(songId, userId, now, true);
    }

    private void insertSongMember(
            long songId,
            long userId,
            Instant now,
            boolean leader
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
                values (?, ?, '기타', ?, ?, ?)
                """,
                songId,
                userId,
                leader,
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
