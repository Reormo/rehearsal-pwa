package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationSettings;
import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationSource;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.RoomException;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSettingsRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomExceptionRepository;
import com.bandclub.rehearsal.song.domain.Song;
import com.bandclub.rehearsal.song.repository.SongMemberRepository;
import com.bandclub.rehearsal.song.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class BookingService {

    private static final Set<Integer> ALLOWED_DURATIONS = Set.of(30, 60, 90, 120, 150, 180);

    private final MembershipService membershipService;
    private final ScheduleService scheduleService;
    private final RoomOperatingHoursPolicy roomOperatingHoursPolicy;
    private final BookingRoundRepository roundRepository;
    private final ReservationSettingsRepository settingsRepository;
    private final ReservationSlotRepository slotRepository;
    private final RoomExceptionRepository exceptionRepository;
    private final ReservationRepository reservationRepository;
    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;
    private final Clock clock;

    public BookingService(
            MembershipService membershipService,
            ScheduleService scheduleService,
            RoomOperatingHoursPolicy roomOperatingHoursPolicy,
            BookingRoundRepository roundRepository,
            ReservationSettingsRepository settingsRepository,
            ReservationSlotRepository slotRepository,
            RoomExceptionRepository exceptionRepository,
            ReservationRepository reservationRepository,
            SongRepository songRepository,
            SongMemberRepository songMemberRepository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.scheduleService = scheduleService;
        this.roomOperatingHoursPolicy = roomOperatingHoursPolicy;
        this.roundRepository = roundRepository;
        this.settingsRepository = settingsRepository;
        this.slotRepository = slotRepository;
        this.exceptionRepository = exceptionRepository;
        this.reservationRepository = reservationRepository;
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
        this.clock = clock;
    }

    @Transactional
    public ReservationView create(
            Long userId,
            Long songId,
            Instant startAt,
            int durationMinutes
    ) {
        ClubMember membership = membershipService.requireMembership(userId);
        scheduleService.ensureCurrentAndNext(membership.getClubId());

        validateStart(startAt);
        validateDurationShape(durationMinutes);

        LocalDate date = startAt.atZone(ScheduleService.SERVICE_ZONE).toLocalDate();
        BookingRound round = requireRound(membership.getClubId(), date);
        validateDurationForRound(durationMinutes, round);

        Instant now = clock.instant();
        validateBookingWindow(now, round);
        validateFutureTime(startAt, now);

        Instant endAt = startAt.plusSeconds(durationMinutes * 60L);
        var operatingHours = roomOperatingHoursPolicy.effective(
                membership.getClubId(),
                date
        );
        validateRoomHours(date, startAt, endAt, operatingHours);

        Song song = songRepository.findForUpdate(songId, membership.getClubId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SONG_NOT_FOUND",
                        "예약할 곡을 찾을 수 없습니다."
                ));
        if (!song.isActive()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_ARCHIVED",
                    "보관된 곡은 새 예약을 만들 수 없습니다."
            );
        }

        boolean leader = songMemberRepository.findBySongIdAndUserId(songId, userId)
                .map(member -> member.isLeader())
                .orElse(false);
        if (!leader) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SONG_LEADER_REQUIRED",
                    "해당 곡의 팀장만 예약할 수 있습니다."
            );
        }

        ReservationSettings settings = settingsRepository.findById(membership.getClubId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.CONFLICT,
                        "RESERVATION_SETTINGS_NOT_READY",
                        "예약 운영 설정이 준비되지 않았습니다."
                ));

        if (!settings.isAllowMultipleReservations()
                && reservationRepository.existsByBookingRoundIdAndSongIdAndStatus(
                round.getId(),
                songId,
                ReservationStatus.ACTIVE
        )) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "MULTIPLE_RESERVATIONS_NOT_ALLOWED",
                    "이 회차에는 이미 해당 팀의 예약이 있습니다."
            );
        }

        List<ReservationSlot> lockedSlots = slotRepository.findRangeForUpdate(
                round.getId(),
                startAt,
                endAt
        );
        validateLockedSlots(lockedSlots, startAt, durationMinutes);
        operatingHours = roomOperatingHoursPolicy.effective(
                membership.getClubId(),
                date
        );
        validateRoomHours(date, startAt, endAt, operatingHours);

        List<RoomException> blockedPeriods = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        membership.getClubId(),
                        date
                );
        if (overlapsBlockedPeriod(date, startAt, endAt, blockedPeriods)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ROOM_TIME_BLOCKED",
                    "선택한 시간에 동아리방 사용 불가 시간이 포함되어 있습니다."
            );
        }

        if (lockedSlots.stream().anyMatch(slot -> slot.getReservationId() != null)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SLOT_ALREADY_RESERVED",
                    "방금 다른 팀이 먼저 예약한 시간이 포함되어 있습니다."
            );
        }

        Reservation reservation = reservationRepository.saveAndFlush(Reservation.team(
                round.getId(),
                song.getId(),
                startAt,
                endAt,
                userId,
                now
        ));
        lockedSlots.forEach(slot -> slot.occupy(reservation.getId()));

        return toView(reservation, song.getTitle());
    }

    @Transactional
    public BookingOptionsView options(
            Long userId,
            LocalDate date,
            int durationMinutes
    ) {
        ClubMember membership = membershipService.requireMembership(userId);
        scheduleService.ensureCurrentAndNext(membership.getClubId());
        validateDurationShape(durationMinutes);

        BookingRound round = requireRound(membership.getClubId(), date);
        validateDurationForRound(durationMinutes, round);

        Instant from = date.atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        List<ReservationSlot> slots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.getId(),
                        from,
                        to
                );
        List<RoomException> blockedPeriods = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        membership.getClubId(),
                        date
                );
        var operatingHours = roomOperatingHoursPolicy.effective(
                membership.getClubId(),
                date
        );

        Instant now = clock.instant();
        boolean acceptingReservations = !now.isBefore(round.getBookingOpenAt())
                && now.isBefore(round.getBookingCloseAt());

        int atoms = durationMinutes / ScheduleService.SLOT_MINUTES;
        List<BookingTimeOptionView> result = new ArrayList<>();
        for (int index = 0; index < slots.size(); index++) {
            Instant candidateStart = slots.get(index).getSlotStartAt();
            Instant candidateEnd = candidateStart.plusSeconds(durationMinutes * 60L);

            if (!candidateStart.isAfter(now)) {
                continue;
            }
            if (!roomOperatingHoursPolicy.contains(
                    date, candidateStart, candidateEnd, operatingHours
            )) {
                continue;
            }
            if (!hasContiguousOpenSlots(slots, index, atoms, candidateStart)) {
                continue;
            }
            if (overlapsBlockedPeriod(date, candidateStart, candidateEnd, blockedPeriods)) {
                continue;
            }

            result.add(new BookingTimeOptionView(candidateStart, candidateEnd));
        }

        return new BookingOptionsView(
                date,
                durationMinutes,
                round.getMaxReservationMinutes(),
                acceptingReservations,
                result
        );
    }

    @Transactional(readOnly = true)
    public List<ReservationView> myUpcoming(Long userId) {
        ClubMember membership = membershipService.requireMembership(userId);
        LinkedHashSet<Long> songIds = new LinkedHashSet<>();
        songMemberRepository.findAllByUserIdOrderByIdAsc(userId)
                .forEach(member -> songIds.add(member.getSongId()));
        if (songIds.isEmpty()) {
            return List.of();
        }

        List<Reservation> reservations = reservationRepository
                .findAllBySongIdInAndStatusAndEndAtAfterOrderByStartAtAsc(
                        songIds,
                        ReservationStatus.ACTIVE,
                        clock.instant()
                );
        if (reservations.isEmpty()) {
            return List.of();
        }

        Map<Long, Song> songs = new LinkedHashMap<>();
        songRepository.findAllById(reservations.stream().map(Reservation::getSongId).distinct().toList())
                .stream()
                .filter(song -> song.getClubId().equals(membership.getClubId()))
                .forEach(song -> songs.put(song.getId(), song));

        return reservations.stream()
                .filter(reservation -> songs.containsKey(reservation.getSongId()))
                .map(reservation -> toView(
                        reservation,
                        songs.get(reservation.getSongId()).getTitle()
                ))
                .toList();
    }

    @Transactional
    public ReservationView move(
            Long userId,
            Long reservationId,
            Instant newStartAt
    ) {
        validateStart(newStartAt);
        TeamEditContext context = requireTeamEditable(userId, reservationId);
        Reservation reservation = context.reservation();
        BookingRound round = context.round();

        validateFutureTime(newStartAt, context.now());
        LocalDate targetDate = newStartAt
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();
        if (targetDate.isBefore(round.getStartDate())
                || targetDate.isAfter(round.getEndDate())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_MOVE_OUTSIDE_ROUND",
                    "예약은 같은 회차 안에서만 이동할 수 있습니다."
            );
        }

        int durationMinutes = reservationDurationMinutes(reservation);
        Instant newEndAt = newStartAt.plusSeconds(durationMinutes * 60L);
        replaceReservationTime(
                context,
                targetDate,
                newStartAt,
                newEndAt
        );
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public ReservationView extend(
            Long userId,
            Long reservationId,
            ReservationBoundary boundary
    ) {
        validateBoundary(boundary);
        TeamEditContext context = requireTeamEditable(userId, reservationId);
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
        validateFutureTime(newStartAt, context.now());

        LocalDate date = reservation.getStartAt()
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();
        replaceReservationTime(context, date, newStartAt, newEndAt);
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public ReservationView shorten(
            Long userId,
            Long reservationId,
            ReservationBoundary boundary
    ) {
        validateBoundary(boundary);
        TeamEditContext context = requireTeamEditable(userId, reservationId);
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
        replaceReservationTime(context, date, newStartAt, newEndAt);
        return toView(reservation, context.song().getTitle());
    }

    @Transactional
    public void cancel(Long userId, Long reservationId) {
        TeamEditContext context = requireTeamEditable(userId, reservationId);
        Reservation reservation = context.reservation();
        List<ReservationSlot> occupiedSlots = slotRepository
                .findAllByReservationIdInForUpdate(List.of(reservation.getId()));
        validateCurrentReservationSlots(reservation, occupiedSlots);

        occupiedSlots.forEach(ReservationSlot::release);
        reservation.cancel(userId, null, context.now());
    }

    private TeamEditContext requireTeamEditable(Long userId, Long reservationId) {
        ClubMember membership = membershipService.requireMembership(userId);
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> reservationNotFound());
        BookingRound round = roundRepository.findByIdAndClubId(
                        reservation.getBookingRoundId(),
                        membership.getClubId()
                )
                .orElseThrow(() -> reservationNotFound());

        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_NOT_ACTIVE",
                    "활성 상태인 예약만 수정할 수 있습니다."
            );
        }

        Instant now = clock.instant();
        if (!now.isBefore(reservation.getStartAt())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_ALREADY_STARTED",
                    "이미 시작한 합주는 관리자만 조정할 수 있습니다."
            );
        }

        Song song = songRepository.findByIdAndClubId(
                        reservation.getSongId(),
                        membership.getClubId()
                )
                .orElseThrow(() -> reservationNotFound());
        if (!song.isActive()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_ARCHIVED",
                    "보관된 곡의 예약은 관리자만 조정할 수 있습니다."
            );
        }

        boolean leader = songMemberRepository
                .findBySongIdAndUserId(song.getId(), userId)
                .map(member -> member.isLeader())
                .orElse(false);
        if (!leader) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SONG_LEADER_REQUIRED",
                    "해당 곡의 팀장만 예약을 수정할 수 있습니다."
            );
        }

        return new TeamEditContext(membership, reservation, round, song, now);
    }

    private void replaceReservationTime(
            TeamEditContext context,
            LocalDate date,
            Instant newStartAt,
            Instant newEndAt
    ) {
        Reservation reservation = context.reservation();
        var operatingHours = roomOperatingHoursPolicy.effective(
                context.membership().getClubId(),
                date
        );
        validateRoomHours(date, newStartAt, newEndAt, operatingHours);

        List<RoomException> blockedPeriods = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        context.membership().getClubId(),
                        date
                );
        if (overlapsBlockedPeriod(date, newStartAt, newEndAt, blockedPeriods)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ROOM_TIME_BLOCKED",
                    "변경하려는 시간에 동아리방 사용 불가 시간이 포함되어 있습니다."
            );
        }

        List<ReservationSlot> currentSlots;
        List<ReservationSlot> targetSlots;
        boolean targetStartsFirst = newStartAt.isBefore(reservation.getStartAt());

        if (targetStartsFirst) {
            targetSlots = slotRepository.findRangeForUpdate(
                    context.round().getId(),
                    newStartAt,
                    newEndAt
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
                    context.round().getId(),
                    newStartAt,
                    newEndAt
            );
        }

        int targetDuration = Math.toIntExact(
                Duration.between(newStartAt, newEndAt).toMinutes()
        );
        validateLockedSlots(targetSlots, newStartAt, targetDuration);
        validateCurrentReservationSlots(reservation, currentSlots);

        if (targetSlots.stream().anyMatch(slot ->
                slot.getReservationId() != null
                        && !reservation.getId().equals(slot.getReservationId()))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SLOT_ALREADY_RESERVED",
                    "방금 다른 팀이 먼저 예약한 시간이 포함되어 있습니다."
            );
        }

        currentSlots.forEach(ReservationSlot::release);
        targetSlots.forEach(slot -> slot.occupy(reservation.getId()));
        reservation.reschedule(newStartAt, newEndAt, context.now());
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

    private int reservationDurationMinutes(Reservation reservation) {
        return Math.toIntExact(Duration.between(
                reservation.getStartAt(),
                reservation.getEndAt()
        ).toMinutes());
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

    private AppException reservationNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND",
                "예약을 찾을 수 없습니다."
        );
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

    private void validateBookingWindow(Instant now, BookingRound round) {
        if (now.isBefore(round.getBookingOpenAt())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_NOT_OPEN",
                    "아직 이 회차의 예약이 열리지 않았습니다."
            );
        }
        if (!now.isBefore(round.getBookingCloseAt())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_CLOSED",
                    "이 회차의 예약 접수가 마감되었습니다."
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
        ZonedDateTime local = startAt.atZone(ScheduleService.SERVICE_ZONE);
        LocalTime time = local.toLocalTime();
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

    private void validateFutureTime(Instant startAt, Instant now) {
        if (!startAt.isAfter(now)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "BOOKING_TIME_PASSED",
                    "이미 지난 시간은 예약할 수 없습니다."
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

    private void validateDurationForRound(int durationMinutes, BookingRound round) {
        if (durationMinutes > round.getMaxReservationMinutes()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_TOO_LONG",
                    "선택한 예약 시간이 이 회차의 최대 예약 시간을 초과합니다."
            );
        }
    }

    private void validateRoomHours(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            RoomOperatingHoursPolicy.Window operatingHours
    ) {
        if (!roomOperatingHoursPolicy.contains(
                date, startAt, endAt, operatingHours
        )) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "RESERVATION_OUTSIDE_ROOM_HOURS",
                    "예약은 해당 날짜의 동아리방 운영시간 범위 안에서만 가능합니다."
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

    private boolean hasContiguousOpenSlots(
            List<ReservationSlot> slots,
            int startIndex,
            int atoms,
            Instant candidateStart
    ) {
        if (startIndex + atoms > slots.size()) {
            return false;
        }
        for (int offset = 0; offset < atoms; offset++) {
            ReservationSlot slot = slots.get(startIndex + offset);
            Instant expectedStart = candidateStart.plusSeconds(
                    offset * ScheduleService.SLOT_MINUTES * 60L
            );
            if (!slot.getSlotStartAt().equals(expectedStart)
                    || slot.getReservationId() != null) {
                return false;
            }
        }
        return true;
    }

    private boolean overlapsBlockedPeriod(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            Collection<RoomException> blockedPeriods
    ) {
        int start = roomOperatingHoursPolicy.minuteOffset(date, startAt);
        int end = roomOperatingHoursPolicy.minuteOffset(date, endAt);
        return blockedPeriods.stream().anyMatch(exception ->
                start < exception.getBlockedEndMinute()
                        && end > exception.getBlockedStartMinute()
        );
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

    private record TeamEditContext(
            ClubMember membership,
            Reservation reservation,
            BookingRound round,
            Song song,
            Instant now
    ) {
    }

    public record BookingTimeOptionView(
            Instant startAt,
            Instant endAt
    ) {
    }

    public record BookingOptionsView(
            LocalDate date,
            int durationMinutes,
            int maxReservationMinutes,
            boolean acceptingReservations,
            List<BookingTimeOptionView> options
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
