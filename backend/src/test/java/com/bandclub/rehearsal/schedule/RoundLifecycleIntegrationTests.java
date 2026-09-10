package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.schedule.service.RoundLifecycleService;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

import static java.time.DayOfWeek.MONDAY;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class RoundLifecycleIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    RoundLifecycleService lifecycleService;

    @Test
    void expiredRoundOperationalDataIsDeletedAndOnlyCurrentNextRemain() {
        Long clubId = jdbcTemplate.queryForObject(
                "select id from clubs order by id limit 1",
                Long.class
        );
        Long userId = jdbcTemplate.queryForObject(
                "select id from users order by id limit 1",
                Long.class
        );

        LocalDate currentMonday = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .with(TemporalAdjusters.previousOrSame(MONDAY));
        LocalDate oldMonday = currentMonday.minusWeeks(1);
        LocalDate oldSunday = oldMonday.plusDays(6);

        Instant createdAt = Instant.now();
        Instant oldOpenAt = oldMonday.minusDays(1)
                .atTime(20, 0)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
        Instant oldCloseAt = oldSunday
                .atTime(22, 0)
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();

        Long oldRoundId = jdbcTemplate.queryForObject(
                """
                insert into booking_rounds (
                    club_id, round_no, start_date, end_date,
                    booking_open_at, booking_close_at,
                    max_reservation_minutes, created_at, updated_at
                )
                values (?, 900001, ?, ?, ?, ?, 90, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                Date.valueOf(oldMonday),
                Date.valueOf(oldSunday),
                Timestamp.from(oldOpenAt),
                Timestamp.from(oldCloseAt),
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        Long songId = jdbcTemplate.queryForObject(
                """
                insert into songs (
                    club_id, title, status, created_by,
                    created_at, updated_at
                )
                values (?, '회차 정리 테스트', 'ACTIVE', ?, ?, ?)
                returning id
                """,
                Long.class,
                clubId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        jdbcTemplate.update(
                """
                insert into song_members (
                    song_id, user_id, session_name, is_leader,
                    created_at, updated_at
                )
                values (?, ?, '기타', true, ?, ?)
                """,
                songId,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        Instant firstStart = oldMonday
                .atTime(LocalTime.of(10, 0))
                .atZone(ZoneId.of("Asia/Seoul"))
                .toInstant();
        Instant secondStart = firstStart.plusSeconds(3600);

        Long firstReservationId = insertReservation(
                oldRoundId,
                songId,
                userId,
                firstStart,
                createdAt
        );
        Long secondReservationId = insertReservation(
                oldRoundId,
                songId,
                userId,
                secondStart,
                createdAt
        );

        jdbcTemplate.update(
                """
                insert into reservation_slots (
                    booking_round_id, slot_start_at,
                    reservation_id, created_at
                )
                values (?, ?, ?, ?)
                """,
                oldRoundId,
                Timestamp.from(firstStart),
                firstReservationId,
                Timestamp.from(createdAt)
        );

        Long swapId = jdbcTemplate.queryForObject(
                """
                insert into swap_requests (
                    requester_reservation_id,
                    target_reservation_id,
                    requested_by,
                    status,
                    requester_start_snapshot,
                    requester_end_snapshot,
                    target_start_snapshot,
                    target_end_snapshot,
                    requested_at
                )
                values (
                    ?, ?, ?, 'PENDING',
                    ?, ?, ?, ?, ?
                )
                returning id
                """,
                Long.class,
                firstReservationId,
                secondReservationId,
                userId,
                Timestamp.from(firstStart),
                Timestamp.from(firstStart.plusSeconds(1800)),
                Timestamp.from(secondStart),
                Timestamp.from(secondStart.plusSeconds(1800)),
                Timestamp.from(createdAt)
        );

        Long stageTypeId = jdbcTemplate.queryForObject(
                "select stage_type_id from songs where id = ?",
                Long.class,
                songId
        );
        jdbcTemplate.update(
                """
                insert into booking_round_stage_windows (
                    booking_round_id,
                    stage_type_id,
                    booking_open_at,
                    booking_close_at,
                    max_reservation_minutes,
                    updated_by,
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                oldRoundId,
                stageTypeId,
                Timestamp.from(oldOpenAt),
                Timestamp.from(oldCloseAt),
                90,
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );

        lifecycleService.reconcileAllClubs();

        assertEquals(
                0,
                count("select count(*) from booking_rounds where id = ?", oldRoundId)
        );
        assertEquals(
                0,
                count(
                        "select count(*) from reservations where id in (?, ?)",
                        firstReservationId,
                        secondReservationId
                )
        );
        assertEquals(
                0,
                count("select count(*) from swap_requests where id = ?", swapId)
        );
        assertEquals(
                0,
                count(
                        "select count(*) from reservation_slots where booking_round_id = ?",
                        oldRoundId
                )
        );
        assertEquals(
                0,
                count(
                        "select count(*) from booking_round_stage_windows where booking_round_id = ?",
                        oldRoundId
                )
        );

        assertEquals(
                2,
                count(
                        """
                        select count(*)
                        from booking_rounds
                        where club_id = ?
                          and start_date in (?, ?)
                        """,
                        clubId,
                        Date.valueOf(currentMonday),
                        Date.valueOf(currentMonday.plusWeeks(1))
                )
        );

        assertEquals(
                1,
                count("select count(*) from songs where id = ?", songId)
        );
    }

    private Long insertReservation(
            Long roundId,
            Long songId,
            Long userId,
            Instant startAt,
            Instant createdAt
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
                    created_at,
                    updated_at
                )
                values (?, ?, ?, ?, 'ACTIVE', 'TEAM', ?, ?, ?)
                returning id
                """,
                Long.class,
                roundId,
                songId,
                Timestamp.from(startAt),
                Timestamp.from(startAt.plusSeconds(1800)),
                userId,
                Timestamp.from(createdAt),
                Timestamp.from(createdAt)
        );
    }

    private int count(String sql, Object... args) {
        Integer value = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                args
        );
        return value == null ? 0 : value;
    }
}
