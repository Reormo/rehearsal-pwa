package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationSettings;
import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.RoomException;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSettingsRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomExceptionRepository;
import com.bandclub.rehearsal.schedule.repository.ScheduleProvisioningLock;
import com.bandclub.rehearsal.song.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    public static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    public static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(22, 0);
    public static final int SLOT_MINUTES = 30;
    public static final int DEFAULT_OPEN_MINUTE = RoomOperatingHoursPolicy.DEFAULT_OPEN_MINUTE;
    public static final int DEFAULT_CLOSE_MINUTE = RoomOperatingHoursPolicy.DEFAULT_CLOSE_MINUTE;
    public static final int DEFAULT_BOOKING_OPEN_LEAD_MINUTES = 1680;
    public static final int DEFAULT_MAX_RESERVATION_MINUTES = 90;

    private static final Set<Integer> ALLOWED_MAX_MINUTES =
            Set.of(30, 60, 90, 120, 150, 180);

    private final MembershipService membershipService;
    private final ReservationSettingsRepository settingsRepository;
    private final BookingRoundRepository roundRepository;
    private final RoomExceptionRepository exceptionRepository;
    private final ReservationSlotRepository slotRepository;
    private final ReservationRepository reservationRepository;
    private final SongRepository songRepository;
    private final ScheduleProvisioningLock provisioningLock;
    private final RoomOperatingHoursPolicy roomOperatingHoursPolicy;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public ScheduleService(
            MembershipService membershipService,
            ReservationSettingsRepository settingsRepository,
            BookingRoundRepository roundRepository,
            RoomExceptionRepository exceptionRepository,
            ReservationSlotRepository slotRepository,
            ReservationRepository reservationRepository,
            SongRepository songRepository,
            ScheduleProvisioningLock provisioningLock,
            RoomOperatingHoursPolicy roomOperatingHoursPolicy,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.settingsRepository = settingsRepository;
        this.roundRepository = roundRepository;
        this.exceptionRepository = exceptionRepository;
        this.slotRepository = slotRepository;
        this.reservationRepository = reservationRepository;
        this.songRepository = songRepository;
        this.provisioningLock = provisioningLock;
        this.roomOperatingHoursPolicy = roomOperatingHoursPolicy;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional
    public CalendarView calendar(Long userId, LocalDate from, LocalDate to) {
        var membership = membershipService.requireMembership(userId);
        validateDateRange(from, to, 62);

        ensureCurrentAndNext(membership.getClubId());

        List<BookingRound> rounds =
                roundRepository.findAllByClubIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(
                        membership.getClubId(),
                        from,
                        to
                );
        Map<LocalDate, List<RoomException>> exceptionsByDate =
                exceptionRepository.findAllByClubIdAndExceptionDateBetweenOrderByExceptionDateAscBlockedStartMinuteAsc(
                                membership.getClubId(),
                                from,
                                to
                        ).stream()
                        .collect(Collectors.groupingBy(
                                RoomException::getExceptionDate,
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        List<DaySummary> days = new ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate date = cursor;
            BookingRound round = rounds.stream()
                    .filter(candidate ->
                            !date.isBefore(candidate.getStartDate()) && !date.isAfter(candidate.getEndDate()))
                    .findFirst()
                    .orElse(null);
            List<RoomException> exceptions = exceptionsByDate.getOrDefault(date, List.of());
            var operatingHours = roomOperatingHoursPolicy.effective(
                    membership.getClubId(),
                    date
            );
            days.add(toDaySummary(
                    date, round, exceptions, operatingHours
            ));
            cursor = cursor.plusDays(1);
        }

        return new CalendarView(from, to, days);
    }

    @Transactional
    public DayScheduleView day(Long userId, LocalDate date) {
        var membership = membershipService.requireMembership(userId);
        ensureCurrentAndNext(membership.getClubId());

        BookingRound round = roundRepository
                .findByClubIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
                        membership.getClubId(),
                        date,
                        date
                )
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_ROUND_NOT_FOUND",
                        "해당 날짜의 예약 회차가 준비되지 않았습니다."
                ));

        List<RoomException> exceptions = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        membership.getClubId(),
                        date
                );
        var operatingHours = roomOperatingHoursPolicy.effective(
                membership.getClubId(),
                date
        );

        Instant from = date.atStartOfDay(SERVICE_ZONE).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(SERVICE_ZONE).toInstant();
        List<ReservationSlot> reservationSlots = slotRepository
                .findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
                        round.getId(),
                        from,
                        to
                );
        List<ReservationSlot> visibleSlots = reservationSlots.stream()
                .filter(slot -> roomOperatingHoursPolicy.containsAtomicSlot(
                        date, slot.getSlotStartAt(), operatingHours
                ))
                .toList();

        List<AtomicSlot> atomicSlots = visibleSlots.stream()
                .map(slot -> toAtomicSlot(slot, exceptions))
                .toList();
        GroupedSlots groupedSlots = groupBookableSlots(
                atomicSlots,
                round.getMaxReservationMinutes()
        );
        List<UnavailableSlotView> unavailableSlots = enrichUnavailableSlots(
                groupUnavailableSlots(atomicSlots)
        );

        return new DayScheduleView(
                date,
                toRoundView(round),
                roomStatus(operatingHours, exceptions),
                toOperatingHoursView(operatingHours),
                exceptions.stream().map(this::toExceptionView).toList(),
                groupedSlots.standardSlots(),
                groupedSlots.remainderSlots(),
                unavailableSlots
        );
    }

    @Transactional
    public SettingsView adminSettings(Long userId) {
        var membership = membershipService.requireAdmin(userId);
        ensureCurrentAndNext(membership.getClubId());
        ReservationSettings settings = settingsRepository.findById(membership.getClubId())
                .orElseThrow();
        return toSettingsView(settings);
    }

    @Transactional
    public List<RoundView> adminRounds(Long userId) {
        var membership = membershipService.requireAdmin(userId);
        ensureCurrentAndNext(membership.getClubId());
        return roundRepository.findAllByClubIdOrderByStartDateAsc(membership.getClubId()).stream()
                .map(this::toRoundView)
                .toList();
    }

    @Transactional
    public List<ExceptionView> adminExceptions(Long userId, LocalDate from, LocalDate to) {
        var membership = membershipService.requireAdmin(userId);
        validateDateRange(from, to, 370);
        return exceptionRepository
                .findAllByClubIdAndExceptionDateBetweenOrderByExceptionDateAscBlockedStartMinuteAsc(
                        membership.getClubId(),
                        from,
                        to
                ).stream()
                .map(this::toExceptionView)
                .toList();
    }

    @Transactional
    public SettingsView updateSettings(
            Long userId,
            boolean allowMultipleReservations,
            int defaultBookingOpenLeadMinutes,
            int defaultMaxReservationMinutes
    ) {
        var membership = membershipService.requireAdmin(userId);
        validateBookingOpenLead(defaultBookingOpenLeadMinutes);
        validateMaxMinutes(defaultMaxReservationMinutes);

        provisioningLock.lockClub(membership.getClubId());
        ReservationSettings settings = ensureSettingsLocked(membership.getClubId());
        Map<String, Object> before = settingsSnapshot(settings);

        settings.update(
                allowMultipleReservations,
                defaultBookingOpenLeadMinutes,
                defaultMaxReservationMinutes,
                userId,
                clock.instant()
        );

        Map<String, Object> after = settingsSnapshot(settings);
        actionLogService.record(
                userId,
                "SCHEDULE_SETTINGS_UPDATE",
                "RESERVATION_SETTINGS",
                membership.getClubId(),
                null,
                before,
                after
        );

        return toSettingsView(settings);
    }

    @Transactional
    public RoundView updateRound(
            Long userId,
            Long roundId,
            Instant bookingOpenAt,
            int maxReservationMinutes
    ) {
        var membership = membershipService.requireAdmin(userId);
        validateMaxMinutes(maxReservationMinutes);

        BookingRound round = roundRepository.findByIdAndClubId(roundId, membership.getClubId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_ROUND_NOT_FOUND",
                        "예약 회차를 찾을 수 없습니다."
                ));

        if (bookingOpenAt == null || !bookingOpenAt.isBefore(round.getBookingCloseAt())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_BOOKING_OPEN_AT",
                    "예약 오픈 시각은 회차 마감 시각보다 빨라야 합니다."
            );
        }

        Map<String, Object> before = roundSnapshot(round);
        round.updatePolicy(bookingOpenAt, maxReservationMinutes, clock.instant());
        Map<String, Object> after = roundSnapshot(round);

        actionLogService.record(
                userId,
                "BOOKING_ROUND_UPDATE",
                "BOOKING_ROUND",
                round.getId(),
                null,
                before,
                after
        );

        return toRoundView(round);
    }

    @Transactional
    public ExceptionView createException(
            Long userId,
            LocalDate date,
            String blockedStartTime,
            String blockedEndTime,
            String reason
    ) {
        var membership = membershipService.requireAdmin(userId);
        int blockedStartMinute = RoomOperatingHoursPolicy.parseBoundary(blockedStartTime);
        int blockedEndMinute = RoomOperatingHoursPolicy.parseBoundary(blockedEndTime);

        provisioningLock.lockClub(membership.getClubId());
        var operatingHours = roomOperatingHoursPolicy.effective(
                membership.getClubId(),
                date
        );
        validateException(
                blockedStartMinute,
                blockedEndMinute,
                reason,
                operatingHours
        );
        List<RoomException> existing = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(
                        membership.getClubId(),
                        date
                );
        if (existing.stream().anyMatch(exception -> rangesOverlap(
                blockedStartMinute,
                blockedEndMinute,
                exception.getBlockedStartMinute(),
                exception.getBlockedEndMinute()
        ))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ROOM_EXCEPTION_OVERLAP",
                    "이미 등록된 사용 불가 시간과 겹칩니다."
            );
        }

        List<Long> canceledReservationIds = cancelReservationsOverlappingBlockedPeriod(
                userId,
                membership.getClubId(),
                date,
                blockedStartMinute,
                blockedEndMinute,
                reason
        );

        Instant now = clock.instant();
        RoomException saved = exceptionRepository.save(RoomException.create(
                membership.getClubId(),
                date,
                blockedStartMinute,
                blockedEndMinute,
                reason.trim(),
                userId,
                now
        ));

        Map<String, Object> after = exceptionSnapshot(saved);
        after.put("canceledReservationIds", canceledReservationIds);
        actionLogService.record(
                userId,
                "ROOM_EXCEPTION_CREATE",
                "ROOM_EXCEPTION",
                saved.getId(),
                reason,
                null,
                after
        );

        return toExceptionView(saved);
    }

    @Transactional
    public void deleteException(Long userId, Long exceptionId) {
        var membership = membershipService.requireAdmin(userId);
        provisioningLock.lockClub(membership.getClubId());
        RoomException exception = exceptionRepository
                .findByIdAndClubId(exceptionId, membership.getClubId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "ROOM_EXCEPTION_NOT_FOUND",
                        "동아리방 사용 불가 시간을 찾을 수 없습니다."
                ));

        Map<String, Object> before = exceptionSnapshot(exception);
        exceptionRepository.delete(exception);

        actionLogService.record(
                userId,
                "ROOM_EXCEPTION_DELETE",
                "ROOM_EXCEPTION",
                exceptionId,
                exception.getReason(),
                before,
                null
        );
    }

    private ReservationSettings ensureSettingsLocked(Long clubId) {
        return settingsRepository.findById(clubId)
                .orElseGet(() -> settingsRepository.save(
                        ReservationSettings.initial(clubId, null, clock.instant())
                ));
    }

    public void ensureCurrentAndNext(Long clubId) {
        LocalDate currentMonday = weekStart(koreanToday());
        LocalDate requiredMonday = currentMonday.plusWeeks(1);

        if (isAlreadyPrepared(clubId, currentMonday, requiredMonday)) {
            return;
        }

        provisioningLock.lockClub(clubId);
        ReservationSettings settings = ensureSettingsLocked(clubId);

        Optional<BookingRound> latestOptional =
                roundRepository.findFirstByClubIdOrderByStartDateDesc(clubId);

        LocalDate nextStart;
        int nextRoundNo;
        if (latestOptional.isEmpty()) {
            nextStart = currentMonday;
            nextRoundNo = 1;
        } else {
            BookingRound latest = latestOptional.get();
            nextStart = latest.getStartDate().plusWeeks(1);
            nextRoundNo = latest.getRoundNo() + 1;
        }

        while (!nextStart.isAfter(requiredMonday)) {
            BookingRound round = createRound(clubId, nextRoundNo, nextStart, settings);
            createSlots(round);
            nextStart = nextStart.plusWeeks(1);
            nextRoundNo++;
        }

        roundRepository.findAllByClubIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(
                        clubId,
                        currentMonday,
                        requiredMonday.plusDays(6)
                ).stream()
                .filter(round -> !slotRepository.existsByBookingRoundId(round.getId()))
                .forEach(this::createSlots);
    }

    private boolean isAlreadyPrepared(
            Long clubId,
            LocalDate currentMonday,
            LocalDate requiredMonday
    ) {
        if (settingsRepository.findById(clubId).isEmpty()) {
            return false;
        }

        Optional<BookingRound> latest =
                roundRepository.findFirstByClubIdOrderByStartDateDesc(clubId);
        if (latest.isEmpty() || latest.get().getStartDate().isBefore(requiredMonday)) {
            return false;
        }

        List<BookingRound> rounds = roundRepository
                .findAllByClubIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(
                        clubId,
                        currentMonday,
                        requiredMonday.plusDays(6)
                );
        boolean coversCurrent = rounds.stream().anyMatch(round ->
                !currentMonday.isBefore(round.getStartDate())
                        && !currentMonday.isAfter(round.getEndDate()));
        boolean coversRequired = rounds.stream().anyMatch(round ->
                !requiredMonday.isBefore(round.getStartDate())
                        && !requiredMonday.isAfter(round.getEndDate()));

        return coversCurrent
                && coversRequired
                && rounds.stream().allMatch(round ->
                slotRepository.existsByBookingRoundId(round.getId()));
    }

    private BookingRound createRound(
            Long clubId,
            int roundNo,
            LocalDate startDate,
            ReservationSettings settings
    ) {
        LocalDate endDate = startDate.plusDays(6);
        Instant startOfRound = startDate.atStartOfDay(SERVICE_ZONE).toInstant();
        Instant bookingOpenAt = startOfRound.minusSeconds(
                settings.getDefaultBookingOpenLeadMinutes() * 60L
        );
        Instant bookingCloseAt = endDate.atTime(DEFAULT_CLOSE_TIME)
                .atZone(SERVICE_ZONE)
                .toInstant();
        Instant now = clock.instant();

        return roundRepository.save(BookingRound.create(
                clubId,
                roundNo,
                startDate,
                endDate,
                bookingOpenAt,
                bookingCloseAt,
                settings.getDefaultMaxReservationMinutes(),
                now
        ));
    }

    private void createSlots(BookingRound round) {
        if (slotRepository.existsByBookingRoundId(round.getId())) {
            return;
        }

        Instant now = clock.instant();
        List<ReservationSlot> slots = new ArrayList<>(336);
        LocalDate date = round.getStartDate();

        while (!date.isAfter(round.getEndDate())) {
            int minute = 0;
            while (minute < RoomOperatingHoursPolicy.DAY_MINUTES) {
                Instant slotStart = roomOperatingHoursPolicy.atMinute(date, minute);
                slots.add(ReservationSlot.empty(round.getId(), slotStart, now));
                minute += SLOT_MINUTES;
            }
            date = date.plusDays(1);
        }

        slotRepository.saveAll(slots);
    }

    private DaySummary toDaySummary(
            LocalDate date,
            BookingRound round,
            List<RoomException> exceptions,
            RoomOperatingHoursPolicy.Window operatingHours
    ) {
        return new DaySummary(
                date,
                round == null ? null : round.getId(),
                round == null ? null : round.getRoundNo(),
                round == null ? null : roundState(round),
                roomStatus(operatingHours, exceptions),
                toOperatingHoursView(operatingHours),
                exceptions.size()
        );
    }

    private AtomicSlot toAtomicSlot(
            ReservationSlot slot,
            List<RoomException> exceptions
    ) {
        ZonedDateTime localStart = slot.getSlotStartAt().atZone(SERVICE_ZONE);
        int start = localStart.getHour() * 60 + localStart.getMinute();
        int end = start + SLOT_MINUTES;

        SlotState state;
        if (isBlockedByRoom(exceptions, start, end)) {
            state = SlotState.CLOSED;
        } else if (slot.getReservationId() != null) {
            state = SlotState.RESERVED;
        } else {
            state = SlotState.OPEN;
        }

        return new AtomicSlot(
                slot.getSlotStartAt(),
                slot.getSlotStartAt().plusSeconds(SLOT_MINUTES * 60L),
                state,
                state == SlotState.RESERVED ? slot.getReservationId() : null
        );
    }

    private GroupedSlots groupBookableSlots(
            List<AtomicSlot> atomicSlots,
            int maxReservationMinutes
    ) {
        int atomsPerStandardSlot = maxReservationMinutes / SLOT_MINUTES;
        List<BookableSlotView> standardSlots = new ArrayList<>();
        List<BookableSlotView> remainderSlots = new ArrayList<>();

        int cursor = 0;
        while (cursor < atomicSlots.size()) {
            if (atomicSlots.get(cursor).state() != SlotState.OPEN) {
                cursor++;
                continue;
            }

            int runStart = cursor;
            while (cursor < atomicSlots.size()
                    && atomicSlots.get(cursor).state() == SlotState.OPEN) {
                cursor++;
            }
            int runEnd = cursor;
            int runSize = runEnd - runStart;
            int fullSlotCount = runSize / atomsPerStandardSlot;

            for (int index = 0; index < fullSlotCount; index++) {
                int chunkStart = runStart + index * atomsPerStandardSlot;
                int chunkEndExclusive = chunkStart + atomsPerStandardSlot;
                standardSlots.add(toBookableSlot(
                        atomicSlots,
                        chunkStart,
                        chunkEndExclusive
                ));
            }

            int remainderStart = runStart + fullSlotCount * atomsPerStandardSlot;
            if (remainderStart < runEnd) {
                remainderSlots.add(toBookableSlot(
                        atomicSlots,
                        remainderStart,
                        runEnd
                ));
            }
        }

        return new GroupedSlots(standardSlots, remainderSlots);
    }

    private BookableSlotView toBookableSlot(
            List<AtomicSlot> atomicSlots,
            int startInclusive,
            int endExclusive
    ) {
        AtomicSlot first = atomicSlots.get(startInclusive);
        AtomicSlot last = atomicSlots.get(endExclusive - 1);
        int durationMinutes = (endExclusive - startInclusive) * SLOT_MINUTES;
        return new BookableSlotView(
                first.startAt(),
                last.endAt(),
                durationMinutes
        );
    }

    private List<UnavailableSlotView> groupUnavailableSlots(List<AtomicSlot> atomicSlots) {
        List<UnavailableSlotView> unavailable = new ArrayList<>();
        int cursor = 0;

        while (cursor < atomicSlots.size()) {
            AtomicSlot current = atomicSlots.get(cursor);
            if (current.state() == SlotState.OPEN) {
                cursor++;
                continue;
            }

            int start = cursor;
            SlotState state = current.state();
            Long reservationId = current.reservationId();
            cursor++;
            while (cursor < atomicSlots.size()) {
                AtomicSlot candidate = atomicSlots.get(cursor);
                if (candidate.state() != state
                        || !Objects.equals(candidate.reservationId(), reservationId)) {
                    break;
                }
                cursor++;
            }

            AtomicSlot first = atomicSlots.get(start);
            AtomicSlot last = atomicSlots.get(cursor - 1);
            unavailable.add(new UnavailableSlotView(
                    first.startAt(),
                    last.endAt(),
                    state,
                    reservationId,
                    null,
                    null
            ));
        }

        return unavailable;
    }

    private List<UnavailableSlotView> enrichUnavailableSlots(
            List<UnavailableSlotView> unavailableSlots
    ) {
        List<Long> reservationIds = unavailableSlots.stream()
                .map(UnavailableSlotView::reservationId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (reservationIds.isEmpty()) {
            return unavailableSlots;
        }

        Map<Long, Reservation> reservationsById = new LinkedHashMap<>();
        reservationRepository.findAllById(reservationIds)
                .forEach(reservation -> reservationsById.put(
                        reservation.getId(),
                        reservation
                ));

        List<Long> songIds = reservationsById.values().stream()
                .map(Reservation::getSongId)
                .distinct()
                .toList();
        Map<Long, String> songTitlesById = new LinkedHashMap<>();
        songRepository.findAllById(songIds)
                .forEach(song -> songTitlesById.put(song.getId(), song.getTitle()));

        return unavailableSlots.stream()
                .map(slot -> {
                    if (slot.reservationId() == null) {
                        return slot;
                    }
                    Reservation reservation = reservationsById.get(slot.reservationId());
                    if (reservation == null) {
                        return slot;
                    }
                    return new UnavailableSlotView(
                            slot.startAt(),
                            slot.endAt(),
                            slot.state(),
                            slot.reservationId(),
                            reservation.getSongId(),
                            songTitlesById.get(reservation.getSongId())
                    );
                })
                .toList();
    }

    private List<Long> cancelReservationsOverlappingBlockedPeriod(
            Long actorUserId,
            Long clubId,
            LocalDate date,
            int blockedStartMinute,
            int blockedEndMinute,
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
        Instant blockedFrom = roomOperatingHoursPolicy.atMinute(date, blockedStartMinute);
        Instant blockedTo = roomOperatingHoursPolicy.atMinute(date, blockedEndMinute);

        // Existing reservations are locked before their slots. After the blocked slot
        // rows are locked, query once more to catch a booking that committed while
        // this transaction was waiting for those same slot rows.
        reservationRepository.findOverlappingForUpdate(
                round.getId(),
                ReservationStatus.ACTIVE,
                blockedFrom,
                blockedTo
        );
        slotRepository.findRangeForUpdate(round.getId(), blockedFrom, blockedTo);
        List<Reservation> reservations = reservationRepository.findOverlappingForUpdate(
                round.getId(),
                ReservationStatus.ACTIVE,
                blockedFrom,
                blockedTo
        );
        if (reservations.isEmpty()) {
            return List.of();
        }

        List<Long> reservationIds = reservations.stream()
                .map(Reservation::getId)
                .sorted()
                .toList();
        List<ReservationSlot> occupiedSlots =
                slotRepository.findAllByReservationIdInForUpdate(reservationIds);

        Instant now = clock.instant();
        String cancellationReason = roomExceptionCancellationReason(reason);
        reservations.forEach(reservation ->
                reservation.cancel(actorUserId, cancellationReason, now));
        Set<Long> canceledIds = Set.copyOf(reservationIds);
        occupiedSlots.stream()
                .filter(slot -> canceledIds.contains(slot.getReservationId()))
                .forEach(ReservationSlot::release);

        return reservationIds;
    }

    private String roomExceptionCancellationReason(String reason) {
        String value = "동아리방 사용 불가 시간 등록: " + reason.trim();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private boolean isBlockedByRoom(
            List<RoomException> exceptions,
            int slotStart,
            int slotEnd
    ) {
        return exceptions.stream().anyMatch(exception -> rangesOverlap(
                slotStart,
                slotEnd,
                exception.getBlockedStartMinute(),
                exception.getBlockedEndMinute()
        ));
    }

    private RoomStatus roomStatus(
            RoomOperatingHoursPolicy.Window operatingHours,
            List<RoomException> exceptions
    ) {
        if (exceptions.isEmpty()) {
            return RoomStatus.OPEN;
        }

        int cursor = operatingHours.openMinute();
        while (cursor < operatingHours.closeMinute()) {
            int end = cursor + SLOT_MINUTES;
            if (!isBlockedByRoom(exceptions, cursor, end)) {
                return RoomStatus.PARTIAL_BLOCKED;
            }
            cursor = end;
        }
        return RoomStatus.CLOSED;
    }

    private boolean rangesOverlap(
            int startA,
            int endA,
            int startB,
            int endB
    ) {
        return startA < endB && endA > startB;
    }

    private RoundView toRoundView(BookingRound round) {
        return new RoundView(
                round.getId(),
                round.getRoundNo(),
                round.getStartDate(),
                round.getEndDate(),
                round.getBookingOpenAt(),
                round.getBookingCloseAt(),
                round.getMaxReservationMinutes(),
                roundState(round)
        );
    }

    private OperatingHoursView toOperatingHoursView(
            RoomOperatingHoursPolicy.Window operatingHours
    ) {
        return new OperatingHoursView(
                operatingHours.openMinute(),
                operatingHours.closeMinute(),
                operatingHours.overridden(),
                operatingHours.reason()
        );
    }

    private ExceptionView toExceptionView(RoomException exception) {
        return new ExceptionView(
                exception.getId(),
                exception.getExceptionDate(),
                exception.getBlockedStartMinute(),
                exception.getBlockedEndMinute(),
                exception.getReason(),
                exception.getCreatedBy(),
                exception.getCreatedAt(),
                exception.getUpdatedAt()
        );
    }

    private SettingsView toSettingsView(ReservationSettings settings) {
        return new SettingsView(
                settings.isAllowMultipleReservations(),
                settings.getDefaultBookingOpenLeadMinutes(),
                settings.getDefaultMaxReservationMinutes(),
                settings.getUpdatedBy(),
                settings.getUpdatedAt()
        );
    }

    private RoundState roundState(BookingRound round) {
        Instant now = clock.instant();
        if (!now.isBefore(round.getBookingCloseAt())) {
            return RoundState.CLOSED;
        }
        if (now.isBefore(round.getBookingOpenAt())) {
            return RoundState.UPCOMING;
        }

        Instant roundStart = round.getStartDate().atStartOfDay(SERVICE_ZONE).toInstant();
        if (now.isBefore(roundStart)) {
            return RoundState.BOOKING_OPEN;
        }
        return RoundState.IN_PROGRESS;
    }

    private LocalDate koreanToday() {
        return LocalDate.now(clock.withZone(SERVICE_ZONE));
    }

    private LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void validateDateRange(LocalDate from, LocalDate to, long maxDays) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_DATE_RANGE",
                    "조회 시작일과 종료일을 확인해주세요."
            );
        }
        if (Duration.between(
                from.atStartOfDay(SERVICE_ZONE),
                to.plusDays(1).atStartOfDay(SERVICE_ZONE)
        ).toDays() > maxDays) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "DATE_RANGE_TOO_LARGE",
                    "조회 기간이 너무 깁니다."
            );
        }
    }

    private void validateMaxMinutes(int minutes) {
        if (!ALLOWED_MAX_MINUTES.contains(minutes)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_MAX_RESERVATION_MINUTES",
                    "최대 예약 시간은 30, 60, 90, 120, 150, 180분 중 하나여야 합니다."
            );
        }
    }

    private void validateBookingOpenLead(int minutes) {
        if (minutes < 0 || minutes > 10080 || minutes % 30 != 0) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_BOOKING_OPEN_LEAD",
                    "기본 예약 오픈 시점은 0~10080분 범위의 30분 단위여야 합니다."
            );
        }
    }

    private void validateException(
            int blockedStartMinute,
            int blockedEndMinute,
            String reason,
            RoomOperatingHoursPolicy.Window operatingHours
    ) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROOM_EXCEPTION_REASON",
                    "예외 사유는 1~500자로 입력해주세요."
            );
        }
        if (blockedStartMinute < operatingHours.openMinute()
                || blockedEndMinute > operatingHours.closeMinute()
                || blockedStartMinute >= blockedEndMinute) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROOM_EXCEPTION_TIME",
                    "사용 불가 시간은 해당 날짜의 실제 운영시간 안에 있어야 합니다."
            );
        }
    }

    private Map<String, Object> settingsSnapshot(ReservationSettings settings) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("allowMultipleReservations", settings.isAllowMultipleReservations());
        snapshot.put("defaultBookingOpenLeadMinutes", settings.getDefaultBookingOpenLeadMinutes());
        snapshot.put("defaultMaxReservationMinutes", settings.getDefaultMaxReservationMinutes());
        return snapshot;
    }

    private Map<String, Object> roundSnapshot(BookingRound round) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("roundNo", round.getRoundNo());
        snapshot.put("startDate", round.getStartDate().toString());
        snapshot.put("endDate", round.getEndDate().toString());
        snapshot.put("bookingOpenAt", round.getBookingOpenAt().toString());
        snapshot.put("bookingCloseAt", round.getBookingCloseAt().toString());
        snapshot.put("maxReservationMinutes", round.getMaxReservationMinutes());
        return snapshot;
    }

    private Map<String, Object> exceptionSnapshot(RoomException exception) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("date", exception.getExceptionDate().toString());
        snapshot.put("blockedStartMinute", exception.getBlockedStartMinute());
        snapshot.put("blockedEndMinute", exception.getBlockedEndMinute());
        snapshot.put("reason", exception.getReason());
        return snapshot;
    }

    private record AtomicSlot(
            Instant startAt,
            Instant endAt,
            SlotState state,
            Long reservationId
    ) {
    }

    private record GroupedSlots(
            List<BookableSlotView> standardSlots,
            List<BookableSlotView> remainderSlots
    ) {
    }

    public enum RoundState {
        UPCOMING,
        BOOKING_OPEN,
        IN_PROGRESS,
        CLOSED
    }

    public record OperatingHoursView(
            int openMinute,
            int closeMinute,
            boolean overridden,
            String reason
    ) {
    }

    public enum SlotState {
        OPEN,
        CLOSED,
        RESERVED
    }

    public enum RoomStatus {
        OPEN,
        PARTIAL_BLOCKED,
        CLOSED
    }

    public record SettingsView(
            boolean allowMultipleReservations,
            int defaultBookingOpenLeadMinutes,
            int defaultMaxReservationMinutes,
            Long updatedBy,
            Instant updatedAt
    ) {
    }

    public record RoundView(
            Long id,
            int roundNo,
            LocalDate startDate,
            LocalDate endDate,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            RoundState state
    ) {
    }

    public record ExceptionView(
            Long id,
            LocalDate date,
            int blockedStartMinute,
            int blockedEndMinute,
            String reason,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record DaySummary(
            LocalDate date,
            Long roundId,
            Integer roundNo,
            RoundState roundState,
            RoomStatus roomStatus,
            OperatingHoursView operatingHours,
            int blockedPeriodCount
    ) {
    }

    public record CalendarView(
            LocalDate from,
            LocalDate to,
            List<DaySummary> days
    ) {
    }

    public record BookableSlotView(
            Instant startAt,
            Instant endAt,
            int durationMinutes
    ) {
    }

    public record UnavailableSlotView(
            Instant startAt,
            Instant endAt,
            SlotState state,
            Long reservationId,
            Long songId,
            String songTitle
    ) {
    }

    public record DayScheduleView(
            LocalDate date,
            RoundView round,
            RoomStatus roomStatus,
            OperatingHoursView operatingHours,
            List<ExceptionView> blockedPeriods,
            List<BookableSlotView> standardSlots,
            List<BookableSlotView> remainderSlots,
            List<UnavailableSlotView> unavailableSlots
    ) {
    }
}
