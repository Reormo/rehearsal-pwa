package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import com.bandclub.rehearsal.schedule.domain.ReservationSource;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.RoomException;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomExceptionRepository;
import com.bandclub.rehearsal.song.domain.Song;
import com.bandclub.rehearsal.song.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminReservationService {

    private static final Set<Integer> ALLOWED_DURATIONS =
            Set.of(30, 60, 90, 120, 150, 180);

    private final MembershipService membershipService;
    private final ScheduleService scheduleService;
    private final RoomOperatingHoursPolicy roomOperatingHoursPolicy;
    private final BookingRoundRepository roundRepository;
    private final ReservationSlotRepository slotRepository;
    private final RoomExceptionRepository exceptionRepository;
    private final ReservationRepository reservationRepository;
    private final SongRepository songRepository;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public AdminReservationService(
            MembershipService membershipService,
            ScheduleService scheduleService,
            RoomOperatingHoursPolicy roomOperatingHoursPolicy,
            BookingRoundRepository roundRepository,
            ReservationSlotRepository slotRepository,
            RoomExceptionRepository exceptionRepository,
            ReservationRepository reservationRepository,
            SongRepository songRepository,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.scheduleService = scheduleService;
        this.roomOperatingHoursPolicy = roomOperatingHoursPolicy;
        this.roundRepository = roundRepository;
        this.slotRepository = slotRepository;
        this.exceptionRepository = exceptionRepository;
        this.reservationRepository = reservationRepository;
        this.songRepository = songRepository;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional
    public ReservationView create(
            Long actorUserId,
            Long songId,
            Instant startAt,
            int durationMinutes,
            String reason
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        String normalizedReason = requireReason(reason);
        scheduleService.ensureCurrentAndNext(actor.getClubId());

        validateStart(startAt);
        validateDurationShape(durationMinutes);
        validateFutureCreation(startAt);

        LocalDate date = startAt.atZone(ScheduleService.SERVICE_ZONE).toLocalDate();
        BookingRound round = requireRound(actor.getClubId(), date);
        validateDurationForRound(durationMinutes, round);

        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        var operatingHours = roomOperatingHoursPolicy.effective(actor.getClubId(), date);
        validateRoomHours(date, startAt, endAt, operatingHours);

        Song song = songRepository.findForUpdate(songId, actor.getClubId())
                .orElseThrow(() -> songNotFound());
        if (!song.isActive()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_ARCHIVED",
                    "보관된 곡에는 새 예약을 만들 수 없습니다."
            );
        }

        List<ReservationSlot> lockedSlots = slotRepository.findRangeForUpdate(
                round.getId(),
                startAt,
                endAt
        );
        validateLockedSlots(lockedSlots, startAt, durationMinutes);

        operatingHours = roomOperatingHoursPolicy.effective(actor.getClubId(), date);
        validateRoomHours(date, startAt, endAt, operatingHours);
        validateBlockedPeriods(actor.getClubId(), date, startAt, endAt);
        validateTargetAvailability(lockedSlots, null);

        Instant now = clock.instant();
        Reservation reservation = reservationRepository.saveAndFlush(Reservation.admin(
                round.getId(),
                song.getId(),
                startAt,
                endAt,
                actorUserId,
                now
        ));
        lockedSlots.forEach(slot -> slot.occupy(reservation.getId()));

        actionLogService.record(
                actorUserId,
                "RESERVATION_ADMIN_CREATE",
                "RESERVATION",
                reservation.getId(),
                normalizedReason,
                null,
                reservationSnapshot(reservation)
        );
        return toView(reservation, song.getTitle());
    }

    @Transactional
    public ReservationView move(
            Long actorUserId,
            Long reservationId,
            Instant newStartAt,
            String reason
    ) {
        validateStart(newStartAt);
        String normalizedReason = requireReason(reason);
        AdminEditContext context = requireAdminEditable(actorUserId, reservationId);
        Reservation reservation = context.reservation();
        BookingRound round = context.round();

        LocalDate targetDate = newStartAt.atZone(ScheduleService.SERVICE_ZONE).toLocalDate();
        if (targetDate.isBefore(round.getStartDate()) || targetDate.isAfter(round.getEndDate())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_MOVE_OUTSIDE_ROUND",
                    "예약은 같은 회차 안에서만 이동할 수 있습니다."
            );
        }

        Map<String, Object> before = reservationSnapshot(reservation);
        int durationMinutes = reservationDurationMinutes(reservation);
        Instant newEndAt = newStartAt.plusSeconds(durationMinutes * 60L);
        replaceReservationTime(context, targetDate, newStartAt, newEndAt);
        recordMutation(
                actorUserId,
                "RESERVATION_ADMIN_MOVE",
                normalizedReason,
                reservation,
                before
        );
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public ReservationView extend(
            Long actorUserId,
            Long reservationId,
            ReservationBoundary boundary,
            String reason
    ) {
        validateBoundary(boundary);
        String normalizedReason = requireReason(reason);
        AdminEditContext context = requireAdminEditable(actorUserId, reservationId);
        Reservation reservation = context.reservation();

        int extendedDuration = reservationDurationMinutes(reservation)
                + ScheduleService.SLOT_MINUTES;
        validateDurationForRound(extendedDuration, context.round());

        Instant newStartAt = boundary == ReservationBoundary.FRONT
                ? reservation.getStartAt().minusSeconds(ScheduleService.SLOT_MINUTES * 60L)
                : reservation.getStartAt();
        Instant newEndAt = boundary == ReservationBoundary.BACK
                ? reservation.getEndAt().plusSeconds(ScheduleService.SLOT_MINUTES * 60L)
                : reservation.getEndAt();
        LocalDate date = reservation.getStartAt()
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();

        Map<String, Object> before = reservationSnapshot(reservation);
        replaceReservationTime(context, date, newStartAt, newEndAt);
        recordMutation(
                actorUserId,
                "RESERVATION_ADMIN_EXTEND",
                normalizedReason,
                reservation,
                before
        );
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public ReservationView shorten(
            Long actorUserId,
            Long reservationId,
            ReservationBoundary boundary,
            String reason
    ) {
        validateBoundary(boundary);
        String normalizedReason = requireReason(reason);
        AdminEditContext context = requireAdminEditable(actorUserId, reservationId);
        Reservation reservation = context.reservation();

        int currentDuration = reservationDurationMinutes(reservation);
        if (currentDuration <= ScheduleService.SLOT_MINUTES) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_MIN_DURATION",
                    "예약은 최소 30분이어야 합니다."
            );
        }

        Instant newStartAt = boundary == ReservationBoundary.FRONT
                ? reservation.getStartAt().plusSeconds(ScheduleService.SLOT_MINUTES * 60L)
                : reservation.getStartAt();
        Instant newEndAt = boundary == ReservationBoundary.BACK
                ? reservation.getEndAt().minusSeconds(ScheduleService.SLOT_MINUTES * 60L)
                : reservation.getEndAt();
        LocalDate date = reservation.getStartAt()
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();

        Map<String, Object> before = reservationSnapshot(reservation);
        replaceReservationTime(context, date, newStartAt, newEndAt);
        recordMutation(
                actorUserId,
                "RESERVATION_ADMIN_SHORTEN",
                normalizedReason,
                reservation,
                before
        );
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public void cancel(
            Long actorUserId,
            Long reservationId,
            String reason
    ) {
        String normalizedReason = requireReason(reason);
        AdminEditContext context = requireAdminEditable(actorUserId, reservationId);
        Reservation reservation = context.reservation();
        Map<String, Object> before = reservationSnapshot(reservation);

        List<ReservationSlot> occupiedSlots = slotRepository
                .findAllByReservationIdInForUpdate(List.of(reservation.getId()));
        validateCurrentReservationSlots(reservation, occupiedSlots);
        occupiedSlots.forEach(ReservationSlot::release);
        reservation.cancel(actorUserId, normalizedReason, context.now());

        actionLogService.record(
                actorUserId,
                "RESERVATION_ADMIN_CANCEL",
                "RESERVATION",
                reservation.getId(),
                normalizedReason,
                before,
                reservationSnapshot(reservation)
        );
    }

    private AdminEditContext requireAdminEditable(
            Long actorUserId,
            Long reservationId
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> reservationNotFound());
        BookingRound round = roundRepository.findByIdAndClubId(
                        reservation.getBookingRoundId(),
                        actor.getClubId()
                )
                .orElseThrow(() -> reservationNotFound());

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_NOT_ACTIVE",
                    "활성 상태인 예약만 조정할 수 있습니다."
            );
        }

        Song song = songRepository.findByIdAndClubId(
                        reservation.getSongId(),
                        actor.getClubId()
                )
                .orElseThrow(() -> reservationNotFound());
        return new AdminEditContext(actor, reservation, round, song, clock.instant());
    }

    private void replaceReservationTime(
            AdminEditContext context,
            LocalDate date,
            Instant newStartAt,
            Instant newEndAt
    ) {
        Reservation reservation = context.reservation();
        var operatingHours = roomOperatingHoursPolicy.effective(
                context.actor().getClubId(),
                date
        );
        validateRoomHours(date, newStartAt, newEndAt, operatingHours);
        validateBlockedPeriods(
                context.actor().getClubId(),
                date,
                newStartAt,
                newEndAt
        );

        List<ReservationSlot> currentSlots;
        List<ReservationSlot> targetSlots;
        if (newStartAt.isBefore(reservation.getStartAt())) {
            targetSlots = slotRepository.findRangeForUpdate(
                    context.round().getId(), newStartAt, newEndAt
            );
            currentSlots = slotRepository.findRangeForUpdate(
                    context.round().getId(),
                    reservation.getStartAt(),
                    reservation.getEndAt()
            );
        } else {
            currentSlots = slotRepository.findRangeForUpdate(
                    context.round().getId(),
                    reservation.getStartAt(),
                    reservation.getEndAt()
            );
            targetSlots = slotRepository.findRangeForUpdate(
                    context.round().getId(), newStartAt, newEndAt
            );
        }

        int targetDuration = Math.toIntExact(
                Duration.between(newStartAt, newEndAt).toMinutes()
        );
        validateLockedSlots(targetSlots, newStartAt, targetDuration);
        validateCurrentReservationSlots(reservation, currentSlots);

        operatingHours = roomOperatingHoursPolicy.effective(
                context.actor().getClubId(),
                date
        );
        validateRoomHours(date, newStartAt, newEndAt, operatingHours);
        validateBlockedPeriods(
                context.actor().getClubId(),
                date,
                newStartAt,
                newEndAt
        );
        validateTargetAvailability(targetSlots, reservation.getId());

        currentSlots.forEach(ReservationSlot::release);
        targetSlots.forEach(slot -> slot.occupy(reservation.getId()));
        reservation.reschedule(newStartAt, newEndAt, context.now());
    }

    private void validateBlockedPeriods(
            Long clubId,
            LocalDate date,
            Instant startAt,
            Instant endAt
    ) {
        List<RoomException> blockedPeriods = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        clubId,
                        date
                );
        if (overlapsBlockedPeriod(date, startAt, endAt, blockedPeriods)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ROOM_TIME_BLOCKED",
                    "선택한 시간에 동아리방 사용 불가 시간이 포함되어 있습니다."
            );
        }
    }

    private boolean overlapsBlockedPeriod(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            Collection<RoomException> blockedPeriods
    ) {
        int startMinute = roomOperatingHoursPolicy.minuteOffset(date, startAt);
        int endMinute = roomOperatingHoursPolicy.minuteOffset(date, endAt);
        return blockedPeriods.stream().anyMatch(exception ->
                startMinute < exception.getBlockedEndMinute()
                        && endMinute > exception.getBlockedStartMinute()
        );
    }

    private void validateRoomHours(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            RoomOperatingHoursPolicy.Window operatingHours
    ) {
        if (!roomOperatingHoursPolicy.contains(date, startAt, endAt, operatingHours)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_OUTSIDE_ROOM_HOURS",
                    "예약은 해당 날짜의 동아리방 운영시간 범위 안에서만 가능합니다."
            );
        }
    }

    private void validateTargetAvailability(
            List<ReservationSlot> slots,
            Long currentReservationId
    ) {
        if (slots.stream().anyMatch(slot ->
                slot.getReservationId() != null
                        && !slot.getReservationId().equals(currentReservationId))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SLOT_ALREADY_RESERVED",
                    "다른 예약이 먼저 차지한 시간이 포함되어 있습니다."
            );
        }
    }

    private void validateLockedSlots(
            List<ReservationSlot> slots,
            Instant startAt,
            int durationMinutes
    ) {
        int expectedCount = durationMinutes / ScheduleService.SLOT_MINUTES;
        if (slots.size() != expectedCount) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_SLOT_NOT_FOUND",
                    "선택한 시간의 예약 슬롯이 완전히 준비되지 않았습니다."
            );
        }
        for (int index = 0; index < slots.size(); index++) {
            Instant expectedStart = startAt.plusSeconds(
                    index * ScheduleService.SLOT_MINUTES * 60L
            );
            if (!slots.get(index).getSlotStartAt().equals(expectedStart)) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "RESERVATION_SLOT_NOT_CONTIGUOUS",
                        "선택한 예약 슬롯이 연속되어 있지 않습니다."
                );
            }
        }
    }

    private void validateCurrentReservationSlots(
            Reservation reservation,
            List<ReservationSlot> currentSlots
    ) {
        int expectedCount = reservationDurationMinutes(reservation)
                / ScheduleService.SLOT_MINUTES;
        if (currentSlots.size() != expectedCount
                || currentSlots.stream().anyMatch(slot ->
                !reservation.getId().equals(slot.getReservationId()))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_SLOT_NOT_FOUND",
                    "기존 예약 슬롯 상태가 올바르지 않습니다."
            );
        }
    }

    private void validateStart(Instant startAt) {
        if (startAt == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_START_REQUIRED",
                    "예약 시작 시간이 필요합니다."
            );
        }
        LocalTime time = startAt.atZone(ScheduleService.SERVICE_ZONE).toLocalTime();
        if (time.getSecond() != 0
                || time.getNano() != 0
                || (time.getMinute() != 0 && time.getMinute() != 30)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RESERVATION_START",
                    "예약 시작 시간은 30분 경계여야 합니다."
            );
        }
    }

    private void validateFutureCreation(Instant startAt) {
        if (!startAt.isAfter(clock.instant())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_TIME_PASSED",
                    "이미 지난 시간에는 새 예약을 만들 수 없습니다."
            );
        }
    }

    private void validateDurationShape(int durationMinutes) {
        if (!ALLOWED_DURATIONS.contains(durationMinutes)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_RESERVATION_DURATION",
                    "예약 시간은 30, 60, 90, 120, 150, 180분 중 하나여야 합니다."
            );
        }
    }

    private void validateDurationForRound(
            int durationMinutes,
            BookingRound round
    ) {
        if (durationMinutes > round.getMaxReservationMinutes()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_TOO_LONG",
                    "예약 시간이 이 회차의 최대 예약 시간을 초과합니다."
            );
        }
    }

    private void validateBoundary(ReservationBoundary boundary) {
        if (boundary == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_BOUNDARY_REQUIRED",
                    "앞쪽 또는 뒤쪽 조정 방향이 필요합니다."
            );
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ADMIN_ACTION_REASON",
                    "관리자 조정 사유는 1~500자로 입력해주세요."
            );
        }
        return reason.trim();
    }

    private BookingRound requireRound(Long clubId, LocalDate date) {
        return roundRepository.findByClubIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        clubId,
                        date,
                        date
                )
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_ROUND_NOT_FOUND",
                        "해당 날짜의 예약 회차가 준비되지 않았습니다."
                ));
    }

    private int reservationDurationMinutes(Reservation reservation) {
        return Math.toIntExact(Duration.between(
                reservation.getStartAt(),
                reservation.getEndAt()
        ).toMinutes());
    }

    private void recordMutation(
            Long actorUserId,
            String actionType,
            String reason,
            Reservation reservation,
            Map<String, Object> before
    ) {
        actionLogService.record(
                actorUserId,
                actionType,
                "RESERVATION",
                reservation.getId(),
                reason,
                before,
                reservationSnapshot(reservation)
        );
    }

    private Map<String, Object> reservationSnapshot(Reservation reservation) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("bookingRoundId", reservation.getBookingRoundId());
        snapshot.put("songId", reservation.getSongId());
        snapshot.put("startAt", reservation.getStartAt().toString());
        snapshot.put("endAt", reservation.getEndAt().toString());
        snapshot.put("status", reservation.getStatus().name());
        snapshot.put("source", reservation.getSource().name());
        snapshot.put("canceledBy", reservation.getCanceledBy());
        snapshot.put("cancellationReason", reservation.getCancellationReason());
        snapshot.put("canceledAt", reservation.getCanceledAt() == null
                ? null
                : reservation.getCanceledAt().toString());
        return snapshot;
    }

    private ReservationView toView(Reservation reservation, String songTitle) {
        return new ReservationView(
                reservation.getId(),
                reservation.getBookingRoundId(),
                reservation.getSongId(),
                songTitle,
                reservation.getStartAt(),
                reservation.getEndAt(),
                reservation.getStatus(),
                reservation.getSource(),
                reservation.getCreatedBy(),
                reservation.getCanceledBy(),
                reservation.getCancellationReason(),
                reservation.getCanceledAt(),
                reservation.getCreatedAt(),
                reservation.getUpdatedAt()
        );
    }

    private AppException reservationNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND",
                "예약을 찾을 수 없습니다."
        );
    }

    private AppException songNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "SONG_NOT_FOUND",
                "예약할 곡을 찾을 수 없습니다."
        );
    }

    private record AdminEditContext(
            ClubMember actor,
            Reservation reservation,
            BookingRound round,
            Song song,
            Instant now
    ) {
    }

    public record ReservationView(
            Long id,
            Long bookingRoundId,
            Long songId,
            String songTitle,
            Instant startAt,
            Instant endAt,
            ReservationStatus status,
            ReservationSource source,
            Long createdBy,
            Long canceledBy,
            String cancellationReason,
            Instant canceledAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
