package com.bandclub.rehearsal.schedule;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationSource;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.service.AdminReservationService;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
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
class AdminReservationIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.4");

    @Autowired
    AdminReservationService adminReservationService;

    @Autowired
    ScheduleService scheduleService;

    @Autowired
    SongService songService;

    @Autowired
    ReservationSlotRepository slotRepository;

    @Autowired
    ReservationRepository reservationRepository;

    @Autowired
    AdminActionLogService actionLogService;

    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void adminCanCreateForceAdjustAndCancelWithAuditReasons() {
        long adminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var round = scheduleService.adminRounds(adminId).stream()
                .filter(candidate -> candidate.startDate().isAfter(today))
                .findFirst()
                .orElseThrow();
        var song = songService.createSong(
                adminId,
                "관리자 예약 테스트",
                adminId,
                "기타"
        );

        var created = adminReservationService.create(
                adminId,
                song.id(),
                at(round.startDate(), 15, 0),
                60,
                "관리자 직접 배정"
        );
        assertEquals(ReservationSource.ADMIN, created.source());
        assertEquals(ReservationStatus.ACTIVE, created.status());

        var moved = adminReservationService.move(
                adminId,
                created.id(),
                at(round.startDate(), 16, 0),
                "합주실 배정 조정"
        );
        assertEquals(at(round.startDate(), 16, 0), moved.startAt());
        assertEquals(at(round.startDate(), 17, 0), moved.endAt());

        var extended = adminReservationService.extend(
                adminId,
                created.id(),
                ReservationBoundary.BACK,
                "30분 추가"
        );
        assertEquals(at(round.startDate(), 17, 30), extended.endAt());

        var shortened = adminReservationService.shorten(
                adminId,
                created.id(),
                ReservationBoundary.FRONT,
                "앞 30분 제거"
        );
        assertEquals(at(round.startDate(), 16, 30), shortened.startAt());
        assertEquals(at(round.startDate(), 17, 30), shortened.endAt());

        adminReservationService.cancel(adminId, created.id(), "관리자 최종 취소");
        assertTrue(slotRepository.findAllByReservationIdInForUpdate(
                java.util.List.of(created.id())
        ).isEmpty());

        var logs = actionLogService.list(adminId, 100);
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_ADMIN_CREATE".equals(log.actionType())
                        && "관리자 직접 배정".equals(log.reason())));
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_ADMIN_MOVE".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_ADMIN_EXTEND".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_ADMIN_SHORTEN".equals(log.actionType())));
        assertTrue(logs.stream().anyMatch(log ->
                "RESERVATION_ADMIN_CANCEL".equals(log.actionType())
                        && "관리자 최종 취소".equals(log.reason())));
    }

    @Test
    @Transactional
    void archivedSongExistingReservationCanStillBeForcedByAdmin() {
        long adminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var round = scheduleService.adminRounds(adminId).stream()
                .filter(candidate -> candidate.startDate().isAfter(today))
                .findFirst()
                .orElseThrow();
        var song = songService.createSong(
                adminId,
                "보관 곡 관리자 조정",
                adminId,
                "보컬"
        );
        var reservation = adminReservationService.create(
                adminId,
                song.id(),
                at(round.startDate(), 12, 0),
                60,
                "초기 배정"
        );
        songService.archiveSong(adminId, song.id());

        var moved = adminReservationService.move(
                adminId,
                reservation.id(),
                at(round.startDate(), 13, 0),
                "보관 후 관리자 조정"
        );
        assertEquals(at(round.startDate(), 13, 0), moved.startAt());
    }


    @Test
    @Transactional
    void adminCanForceMoveReservationAfterItHasStarted() {
        long adminId = superAdminId();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        var round = scheduleService.adminRounds(adminId).stream()
                .filter(candidate -> !today.isBefore(candidate.startDate())
                        && !today.isAfter(candidate.endDate()))
                .findFirst()
                .orElseThrow();
        var song = songService.createSong(
                adminId,
                "시작 후 강제 조정 테스트",
                adminId,
                "드럼"
        );

        Instant startAt = at(today, 10, 0);
        Instant endAt = at(today, 11, 0);
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.team(
                round.id(),
                song.id(),
                startAt,
                endAt,
                adminId,
                Instant.now().minusSeconds(3600)
        ));
        var slots = slotRepository.findRangeForUpdate(round.id(), startAt, endAt);
        assertEquals(2, slots.size());
        slots.forEach(slot -> slot.occupy(reservation.getId()));
        slotRepository.flush();

        var moved = adminReservationService.move(
                adminId,
                reservation.getId(),
                at(today, 11, 0),
                "시작 후 관리자 강제 이동"
        );
        assertEquals(at(today, 11, 0), moved.startAt());
        assertEquals(at(today, 12, 0), moved.endAt());
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
