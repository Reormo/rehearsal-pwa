package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.RoomOperatingHours;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomOperatingHoursRepository;
import com.bandclub.rehearsal.schedule.repository.ScheduleProvisioningLock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class AdminRoomOperatingHoursService {

    private final MembershipService membershipService;
    private final RoomOperatingHoursRepository operatingHoursRepository;
    private final RoomOperatingHoursPolicy operatingHoursPolicy;
    private final BookingRoundRepository roundRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationSlotRepository slotRepository;
    private final ScheduleProvisioningLock provisioningLock;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public AdminRoomOperatingHoursService(
            MembershipService membershipService,
            RoomOperatingHoursRepository operatingHoursRepository,
            RoomOperatingHoursPolicy operatingHoursPolicy,
            BookingRoundRepository roundRepository,
            ReservationRepository reservationRepository,
            ReservationSlotRepository slotRepository,
            ScheduleProvisioningLock provisioningLock,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.operatingHoursRepository = operatingHoursRepository;
        this.operatingHoursPolicy = operatingHoursPolicy;
        this.roundRepository = roundRepository;
        this.reservationRepository = reservationRepository;
        this.slotRepository = slotRepository;
        this.provisioningLock = provisioningLock;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperatingHoursView effective(Long actorUserId, LocalDate date) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        requireDate(date);
        return toView(date, operatingHoursPolicy.effective(actor.getClubId(), date));
    }

    @Transactional(readOnly = true)
    public List<OperatingHoursView> overrides(
            Long actorUserId,
            LocalDate from,
            LocalDate to
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        validateDateRange(from, to);
        return operatingHoursRepository
                .findAllByClubIdAndOperatingDateBetweenOrderByOperatingDateAsc(
                        actor.getClubId(), from, to
                ).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public UpdateResult override(
            Long actorUserId,
            LocalDate date,
            String openBoundary,
            String closeBoundary,
            String reason
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        requireDate(date);
        String normalizedReason = requireReason(reason);
        int openMinute = RoomOperatingHoursPolicy.parseBoundary(openBoundary);
        int closeMinute = RoomOperatingHoursPolicy.parseBoundary(closeBoundary);
        validateWindow(openMinute, closeMinute);

        provisioningLock.lockClub(actor.getClubId());
        Optional<RoomOperatingHours> existing = operatingHoursRepository
                .findByClubIdAndOperatingDate(actor.getClubId(), date);
        Map<String, Object> before = windowSnapshot(
                date,
                existing.map(this::toWindow)
                        .orElseGet(this::defaultWindow)
        );

        RoomOperatingHoursPolicy.Window newWindow = new RoomOperatingHoursPolicy.Window(
                openMinute,
                closeMinute,
                true,
                normalizedReason
        );
        List<Long> canceledReservationIds = cancelOutsideWindow(
                actorUserId,
                actor.getClubId(),
                date,
                newWindow,
                normalizedReason
        );

        Instant now = clock.instant();
        RoomOperatingHours saved;
        if (existing.isPresent()) {
            saved = existing.get();
            saved.update(
                    openMinute,
                    closeMinute,
                    normalizedReason,
                    actorUserId,
                    now
            );
        } else {
            saved = operatingHoursRepository.save(RoomOperatingHours.create(
                    actor.getClubId(),
                    date,
                    openMinute,
                    closeMinute,
                    normalizedReason,
                    actorUserId,
                    now
            ));
        }

        Map<String, Object> after = windowSnapshot(date, toWindow(saved));
        after.put("canceledReservationIds", canceledReservationIds);
        actionLogService.record(
                actorUserId,
                "ROOM_OPERATING_HOURS_OVERRIDE",
                "ROOM_OPERATING_HOURS",
                saved.getId(),
                normalizedReason,
                before,
                after
        );

        return new UpdateResult(toView(saved), canceledReservationIds);
    }

    @Transactional
    public UpdateResult restoreDefault(
            Long actorUserId,
            LocalDate date,
            String reason
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        requireDate(date);
        String normalizedReason = requireReason(reason);

        provisioningLock.lockClub(actor.getClubId());
        Optional<RoomOperatingHours> existing = operatingHoursRepository
                .findByClubIdAndOperatingDate(actor.getClubId(), date);
        RoomOperatingHoursPolicy.Window beforeWindow = existing
                .map(this::toWindow)
                .orElseGet(this::defaultWindow);
        RoomOperatingHoursPolicy.Window defaultWindow = defaultWindow();

        List<Long> canceledReservationIds = cancelOutsideWindow(
                actorUserId,
                actor.getClubId(),
                date,
                defaultWindow,
                normalizedReason
        );

        Long targetId = existing.map(RoomOperatingHours::getId).orElse(null);
        existing.ifPresent(operatingHoursRepository::delete);

        Map<String, Object> after = windowSnapshot(date, defaultWindow);
        after.put("canceledReservationIds", canceledReservationIds);
        actionLogService.record(
                actorUserId,
                "ROOM_OPERATING_HOURS_RESTORE_DEFAULT",
                "ROOM_OPERATING_HOURS",
                targetId,
                normalizedReason,
                windowSnapshot(date, beforeWindow),
                after
        );

        return new UpdateResult(
                toView(date, defaultWindow),
                canceledReservationIds
        );
    }

    private List<Long> cancelOutsideWindow(
            Long actorUserId,
            Long clubId,
            LocalDate date,
            RoomOperatingHoursPolicy.Window newWindow,
            String reason
    ) {
        Optional<BookingRound> roundOptional =
                roundRepository.findByClubIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        clubId,
                        date,
                        date
                );
        if (roundOptional.isEmpty()) {
            return List.of();
        }

        BookingRound round = roundOptional.get();
        Instant dayStart = date.atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();

        reservationRepository.findOverlappingForUpdate(
                round.getId(),
                ReservationStatus.ACTIVE,
                dayStart,
                dayEnd
        );
        List<ReservationSlot> daySlots = slotRepository.findRangeForUpdate(
                round.getId(),
                dayStart,
                dayEnd
        );
        List<Reservation> reservations = reservationRepository.findOverlappingForUpdate(
                round.getId(),
                ReservationStatus.ACTIVE,
                dayStart,
                dayEnd
        );

        List<Reservation> toCancel = reservations.stream()
                .filter(reservation -> !operatingHoursPolicy.contains(
                        date,
                        reservation.getStartAt(),
                        reservation.getEndAt(),
                        newWindow
                ))
                .toList();
        if (toCancel.isEmpty()) {
            return List.of();
        }

        List<Long> ids = toCancel.stream()
                .map(Reservation::getId)
                .sorted()
                .toList();
        Set<Long> idSet = Set.copyOf(ids);
        Instant now = clock.instant();
        String cancellationReason = cancellationReason(reason);

        for (Reservation reservation : toCancel) {
            Map<String, Object> before = reservationSnapshot(reservation);
            reservation.cancel(actorUserId, cancellationReason, now);
            actionLogService.record(
                    actorUserId,
                    "RESERVATION_CANCELED_BY_OPERATING_HOURS",
                    "RESERVATION",
                    reservation.getId(),
                    reason,
                    before,
                    reservationSnapshot(reservation)
            );
        }
        daySlots.stream()
                .filter(slot -> slot.getReservationId() != null && idSet.contains(slot.getReservationId()))
                .forEach(ReservationSlot::release);
        return ids;
    }

    private String cancellationReason(String reason) {
        String value = "동아리방 운영시간 변경: " + reason;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private RoomOperatingHoursPolicy.Window toWindow(RoomOperatingHours value) {
        return new RoomOperatingHoursPolicy.Window(
                value.getOpenMinute(),
                value.getCloseMinute(),
                true,
                value.getReason()
        );
    }

    private RoomOperatingHoursPolicy.Window defaultWindow() {
        return new RoomOperatingHoursPolicy.Window(
                RoomOperatingHoursPolicy.DEFAULT_OPEN_MINUTE,
                RoomOperatingHoursPolicy.DEFAULT_CLOSE_MINUTE,
                false,
                null
        );
    }

    private OperatingHoursView toView(RoomOperatingHours value) {
        return new OperatingHoursView(
                value.getOperatingDate(),
                value.getOpenMinute(),
                value.getCloseMinute(),
                true,
                value.getReason()
        );
    }

    private OperatingHoursView toView(
            LocalDate date,
            RoomOperatingHoursPolicy.Window window
    ) {
        return new OperatingHoursView(
                date,
                window.openMinute(),
                window.closeMinute(),
                window.overridden(),
                window.reason()
        );
    }

    private void validateWindow(int openMinute, int closeMinute) {
        if (openMinute >= closeMinute) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROOM_OPERATING_HOURS",
                    "운영 시작 시간은 종료 시간보다 빨라야 합니다."
            );
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ADMIN_ACTION_REASON",
                    "관리자 변경 사유는 1~500자로 입력해주세요."
            );
        }
        return reason.trim();
    }

    private void requireDate(LocalDate date) {
        if (date == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "OPERATING_DATE_REQUIRED",
                    "운영시간을 설정할 날짜가 필요합니다."
            );
        }
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        requireDate(from);
        requireDate(to);
        if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 370) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "운영시간 조회 기간을 확인해주세요."
            );
        }
    }

    private Map<String, Object> windowSnapshot(
            LocalDate date,
            RoomOperatingHoursPolicy.Window window
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("date", date.toString());
        snapshot.put("openMinute", window.openMinute());
        snapshot.put("closeMinute", window.closeMinute());
        snapshot.put("overridden", window.overridden());
        snapshot.put("reason", window.reason());
        return snapshot;
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

    public record OperatingHoursView(
            LocalDate date,
            int openMinute,
            int closeMinute,
            boolean overridden,
            String reason
    ) {
    }

    public record UpdateResult(
            OperatingHoursView operatingHours,
            List<Long> canceledReservationIds
    ) {
    }
}
