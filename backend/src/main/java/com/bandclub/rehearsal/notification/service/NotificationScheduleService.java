package com.bandclub.rehearsal.notification.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class NotificationScheduleService {

    private static final Duration BOOKING_OPEN_LOOKBACK =
            Duration.ofMinutes(2);
    private static final Duration BOOKING_PRE_NOTICE =
            Duration.ofMinutes(10);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("M월 d일 HH:mm");
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final WebPushService webPushService;

    public NotificationScheduleService(
            JdbcTemplate jdbcTemplate,
            WebPushService webPushService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.webPushService = webPushService;
    }

    public ProcessingResult process(Instant now) {
        int preOpen = processBookingPreOpen(now);
        int open = processBookingOpen(now);
        int reminders = processRehearsalReminders(now);
        return new ProcessingResult(preOpen, open, reminders);
    }

    private int processBookingPreOpen(Instant now) {
        List<BookingOpenCandidate> candidates = jdbcTemplate.query(
                """
                select
                    br.id,
                    br.round_no,
                    br.booking_open_at,
                    cm.user_id
                from booking_rounds br
                join club_members cm on cm.club_id = br.club_id
                join users u on u.id = cm.user_id
                where u.status = 'ACTIVE'
                  and br.booking_open_at > ?
                  and br.booking_open_at <= ?
                  and br.booking_close_at > ?
                order by br.booking_open_at asc, cm.user_id asc
                """,
                (rs, rowNum) -> new BookingOpenCandidate(
                        rs.getLong("id"),
                        rs.getInt("round_no"),
                        rs.getTimestamp("booking_open_at").toInstant(),
                        rs.getLong("user_id")
                ),
                Timestamp.from(now),
                Timestamp.from(now.plus(BOOKING_PRE_NOTICE)),
                Timestamp.from(now)
        );

        int created = 0;
        for (BookingOpenCandidate candidate : candidates) {
            String dedupeKey =
                    "booking-open-10:" + candidate.roundId()
                            + ":" + candidate.userId();
            String title = "예약 오픈이 곧 시작돼요";
            String body = candidate.roundNo() + "회차 예약이 "
                    + DATE_TIME.format(
                    candidate.bookingOpenAt().atZone(SEOUL)
            )
                    + "에 열립니다.";

            if (createNotification(
                    candidate.userId(),
                    "BOOKING_OPEN_10_MIN",
                    title,
                    body,
                    "/schedule",
                    dedupeKey,
                    now
            )) {
                webPushService.sendToUser(
                        candidate.userId(),
                        title,
                        body,
                        "/schedule",
                        "booking-open-10-" + candidate.roundId()
                );
                created++;
            }
        }
        return created;
    }

    private int processBookingOpen(Instant now) {
        List<BookingOpenCandidate> candidates = jdbcTemplate.query(
                """
                select
                    br.id,
                    br.round_no,
                    br.booking_open_at,
                    cm.user_id
                from booking_rounds br
                join club_members cm on cm.club_id = br.club_id
                join users u on u.id = cm.user_id
                where u.status = 'ACTIVE'
                  and br.booking_open_at <= ?
                  and br.booking_open_at > ?
                  and br.booking_close_at > ?
                order by br.booking_open_at asc, cm.user_id asc
                """,
                (rs, rowNum) -> new BookingOpenCandidate(
                        rs.getLong("id"),
                        rs.getInt("round_no"),
                        rs.getTimestamp("booking_open_at").toInstant(),
                        rs.getLong("user_id")
                ),
                Timestamp.from(now),
                Timestamp.from(now.minus(BOOKING_OPEN_LOOKBACK)),
                Timestamp.from(now)
        );

        int created = 0;
        for (BookingOpenCandidate candidate : candidates) {
            String dedupeKey =
                    "booking-open:" + candidate.roundId()
                            + ":" + candidate.userId();
            String title = "합주 예약이 열렸어요";
            String body = candidate.roundNo()
                    + "회차 예약이 시작되었습니다. 지금 시간표에서 예약할 수 있어요.";

            if (createNotification(
                    candidate.userId(),
                    "BOOKING_OPEN",
                    title,
                    body,
                    "/schedule",
                    dedupeKey,
                    now
            )) {
                webPushService.sendToUser(
                        candidate.userId(),
                        title,
                        body,
                        "/schedule",
                        "booking-open-" + candidate.roundId()
                );
                created++;
            }
        }
        return created;
    }

    private int processRehearsalReminders(Instant now) {
        List<RehearsalReminderCandidate> candidates = jdbcTemplate.query(
                """
                select
                    r.id as reservation_id,
                    r.start_at,
                    r.end_at,
                    s.title as song_title,
                    uns.user_id,
                    uns.rehearsal_reminder_minutes
                from reservations r
                join songs s on s.id = r.song_id
                join song_members sm on sm.song_id = r.song_id
                join users u on u.id = sm.user_id
                join user_notification_settings uns
                  on uns.user_id = sm.user_id
                where r.status = 'ACTIVE'
                  and u.status = 'ACTIVE'
                  and uns.rehearsal_reminder_minutes is not null
                  and r.start_at > ?
                  and (
                      r.start_at
                      - (uns.rehearsal_reminder_minutes * interval '1 minute')
                  ) <= ?
                order by r.start_at asc, uns.user_id asc
                """,
                (rs, rowNum) -> new RehearsalReminderCandidate(
                        rs.getLong("reservation_id"),
                        rs.getString("song_title"),
                        rs.getTimestamp("start_at").toInstant(),
                        rs.getTimestamp("end_at").toInstant(),
                        rs.getLong("user_id"),
                        rs.getInt("rehearsal_reminder_minutes")
                ),
                Timestamp.from(now),
                Timestamp.from(now)
        );

        int created = 0;
        for (RehearsalReminderCandidate candidate : candidates) {
            String dedupeKey =
                    "rehearsal-reminder:"
                            + candidate.reservationId()
                            + ":" + candidate.userId()
                            + ":" + candidate.startAt().getEpochSecond()
                            + ":" + candidate.reminderMinutes();

            String title = "합주 리마인더";
            String body = candidate.songTitle()
                    + " · "
                    + DATE_TIME.format(candidate.startAt().atZone(SEOUL))
                    + "~"
                    + TIME.format(candidate.endAt().atZone(SEOUL))
                    + " 합주가 곧 시작됩니다.";

            if (createNotification(
                    candidate.userId(),
                    "REHEARSAL_REMINDER",
                    title,
                    body,
                    "/schedule",
                    dedupeKey,
                    now
            )) {
                webPushService.sendToUser(
                        candidate.userId(),
                        title,
                        body,
                        "/schedule",
                        "rehearsal-" + candidate.reservationId()
                );
                created++;
            }
        }
        return created;
    }

    private boolean createNotification(
            Long userId,
            String type,
            String title,
            String body,
            String linkPath,
            String dedupeKey,
            Instant now
    ) {
        List<Long> insertedIds = jdbcTemplate.query(
                """
                insert into notifications (
                    user_id,
                    type,
                    title,
                    body,
                    link_path,
                    dedupe_key,
                    created_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (dedupe_key) do nothing
                returning id
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                type,
                title,
                body,
                linkPath,
                dedupeKey,
                Timestamp.from(now)
        );
        return !insertedIds.isEmpty();
    }

    private record BookingOpenCandidate(
            Long roundId,
            int roundNo,
            Instant bookingOpenAt,
            Long userId
    ) {
    }

    private record RehearsalReminderCandidate(
            Long reservationId,
            String songTitle,
            Instant startAt,
            Instant endAt,
            Long userId,
            int reminderMinutes
    ) {
    }

    public record ProcessingResult(
            int bookingPreOpenCreated,
            int bookingOpenCreated,
            int rehearsalRemindersCreated
    ) {
    }
}
