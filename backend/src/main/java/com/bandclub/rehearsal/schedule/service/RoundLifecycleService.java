package com.bandclub.rehearsal.schedule.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class RoundLifecycleService {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final JdbcTemplate jdbcTemplate;
    private final ScheduleService scheduleService;
    private final Clock clock;

    public RoundLifecycleService(
            JdbcTemplate jdbcTemplate,
            ScheduleService scheduleService,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.scheduleService = scheduleService;
        this.clock = clock;
    }

    @Transactional
    public RolloverResult reconcileAllClubs() {
        LocalDate currentMonday = currentMonday();
        List<Long> clubIds = jdbcTemplate.queryForList(
                "select id from clubs order by id",
                Long.class
        );

        int deletedRounds = 0;
        for (Long clubId : clubIds) {
            deletedRounds += purgeExpiredOperationalData(
                    clubId,
                    currentMonday
            );
        }

        for (Long clubId : clubIds) {
            scheduleService.ensureCurrentAndNext(clubId);
        }

        return new RolloverResult(
                clubIds.size(),
                deletedRounds,
                currentMonday
        );
    }

    private int purgeExpiredOperationalData(
            Long clubId,
            LocalDate currentMonday
    ) {
        Date monday = Date.valueOf(currentMonday);

        Integer expiredRoundCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from booking_rounds
                where club_id = ?
                  and end_date < ?
                """,
                Integer.class,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using room_exceptions re
                where re.club_id = ?
                  and re.exception_date < ?
                  and aal.club_id = re.club_id
                  and aal.target_id = re.id
                  and aal.action_type like 'ROOM_EXCEPTION_%'
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using room_operating_hours roh
                where roh.club_id = ?
                  and roh.operating_date < ?
                  and aal.club_id = roh.club_id
                  and aal.target_id = roh.id
                  and aal.action_type like 'ROOM_%HOURS%'
                """,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from room_exceptions
                where club_id = ?
                  and exception_date < ?
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from room_operating_hours
                where club_id = ?
                  and operating_date < ?
                """,
                clubId,
                monday
        );

        if (expiredRoundCount == null || expiredRoundCount == 0) {
            return 0;
        }

        jdbcTemplate.update(
                """
                delete from notifications n
                using booking_rounds br
                where br.club_id = ?
                  and br.end_date < ?
                  and (
                      n.dedupe_key like 'booking-open:' || br.id || ':%'
                      or n.dedupe_key like 'booking-open-10:' || br.id || ':%'
                  )
                """,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from notifications n
                using reservations r, booking_rounds br
                where r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                  and (
                      n.dedupe_key like 'rehearsal-reminder:' || r.id || ':%'
                      or n.dedupe_key like 'reservation-canceled:' || r.id || ':%'
                  )
                """,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from notifications n
                using swap_requests sr, reservations r, booking_rounds br
                where (
                        sr.requester_reservation_id = r.id
                        or sr.target_reservation_id = r.id
                    )
                  and r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                  and n.dedupe_key like 'swap-%:' || sr.id || ':%'
                """,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using booking_rounds br
                where br.club_id = ?
                  and br.end_date < ?
                  and aal.club_id = br.club_id
                  and aal.target_id = br.id
                  and aal.action_type like 'BOOKING_ROUND_%'
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using booking_round_stage_windows brsw, booking_rounds br
                where brsw.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                  and aal.club_id = br.club_id
                  and aal.target_id = brsw.id
                  and aal.action_type like 'BOOKING_STAGE_WINDOW_%'
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using reservations r, booking_rounds br
                where r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                  and aal.club_id = br.club_id
                  and aal.target_id = r.id
                  and aal.action_type like '%RESERVATION%'
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from admin_action_logs aal
                using swap_requests sr, reservations r, booking_rounds br
                where (
                        sr.requester_reservation_id = r.id
                        or sr.target_reservation_id = r.id
                    )
                  and r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                  and aal.club_id = br.club_id
                  and aal.target_id = sr.id
                  and aal.action_type like 'SWAP_%'
                """,
                clubId,
                monday
        );

        jdbcTemplate.update(
                """
                delete from swap_requests sr
                using reservations r, booking_rounds br
                where (
                        sr.requester_reservation_id = r.id
                        or sr.target_reservation_id = r.id
                    )
                  and r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from reservation_slots rs
                using booking_rounds br
                where rs.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from reservations r
                using booking_rounds br
                where r.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from booking_round_stage_windows brsw
                using booking_rounds br
                where brsw.booking_round_id = br.id
                  and br.club_id = ?
                  and br.end_date < ?
                """,
                clubId,
                monday
        );
        jdbcTemplate.update(
                """
                delete from booking_rounds
                where club_id = ?
                  and end_date < ?
                """,
                clubId,
                monday
        );

        return expiredRoundCount;
    }

    private LocalDate currentMonday() {
        LocalDate today = clock.instant()
                .atZone(SEOUL)
                .toLocalDate();
        return today.with(
                TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)
        );
    }

    public record RolloverResult(
            int clubCount,
            int deletedRoundCount,
            LocalDate currentMonday
    ) {
    }
}
