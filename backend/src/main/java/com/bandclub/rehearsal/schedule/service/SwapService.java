package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationSettings;
import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.domain.RoomException;
import com.bandclub.rehearsal.schedule.domain.SwapRequest;
import com.bandclub.rehearsal.schedule.domain.SwapRequestStatus;
import com.bandclub.rehearsal.schedule.repository.ReservationRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSettingsRepository;
import com.bandclub.rehearsal.schedule.repository.ReservationSlotRepository;
import com.bandclub.rehearsal.schedule.repository.RoomExceptionRepository;
import com.bandclub.rehearsal.schedule.repository.SwapRequestRepository;
import com.bandclub.rehearsal.song.domain.Song;
import com.bandclub.rehearsal.song.domain.SongMember;
import com.bandclub.rehearsal.song.repository.SongMemberRepository;
import com.bandclub.rehearsal.song.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SwapService {

    private final MembershipService membershipService;
    private final ReservationRepository reservationRepository;
    private final ReservationSettingsRepository settingsRepository;
    private final ReservationSlotRepository slotRepository;
    private final RoomExceptionRepository exceptionRepository;
    private final SwapRequestRepository swapRequestRepository;
    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;
    private final RoomOperatingHoursPolicy roomOperatingHoursPolicy;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public SwapService(
            MembershipService membershipService,
            ReservationRepository reservationRepository,
            ReservationSettingsRepository settingsRepository,
            ReservationSlotRepository slotRepository,
            RoomExceptionRepository exceptionRepository,
            SwapRequestRepository swapRequestRepository,
            SongRepository songRepository,
            SongMemberRepository songMemberRepository,
            RoomOperatingHoursPolicy roomOperatingHoursPolicy,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.reservationRepository = reservationRepository;
        this.settingsRepository = settingsRepository;
        this.slotRepository = slotRepository;
        this.exceptionRepository = exceptionRepository;
        this.swapRequestRepository = swapRequestRepository;
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
        this.roomOperatingHoursPolicy = roomOperatingHoursPolicy;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SwapView> mine(Long userId) {
        ClubMember membership = membershipService.requireMembership(userId);
        Set<Long> leaderSongIds = leaderSongIds(userId);
        if (leaderSongIds.isEmpty()) {
            return List.of();
        }

        List<Reservation> reservations = reservationRepository.findAllBySongIdIn(leaderSongIds);
        if (reservations.isEmpty()) {
            return List.of();
        }

        Set<Long> reservationIds = reservations.stream()
                .map(Reservation::getId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        return swapRequestRepository.findAllForReservations(reservationIds).stream()
                .map(request -> toView(request, membership.getClubId(), userId, false))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CandidateView> candidates(Long userId, Long requesterReservationId) {
        ClubMember membership = membershipService.requireMembership(userId);
        Reservation requester = reservationRepository.findById(requesterReservationId)
                .orElseThrow(this::reservationNotFound);
        Song requesterSong = requireSongInClub(requester.getSongId(), membership.getClubId());
        requireLeader(userId, requesterSong.getId(), "교환 요청은 해당 팀장만 만들 수 있습니다.");
        requireTeamSwapEligibleReservation(requester, requesterSong, clock.instant());

        if (swapRequestRepository.existsPendingParticipation(List.of(requester.getId()))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_RESERVATION_ALREADY_PENDING",
                    "이 예약은 이미 다른 교환 요청에 참여 중입니다."
            );
        }

        Map<Long, Song> songs = new LinkedHashMap<>();
        songRepository.findAllByClubIdOrderByIdAsc(membership.getClubId()).stream()
                .filter(Song::isActive)
                .forEach(song -> songs.put(song.getId(), song));

        if (songs.isEmpty()) {
            return List.of();
        }

        Instant now = clock.instant();
        return reservationRepository
                .findAllBySongIdInAndStatusAndEndAtAfterOrderByStartAtAsc(
                        songs.keySet(),
                        ReservationStatus.ACTIVE,
                        now
                ).stream()
                .filter(candidate -> !candidate.getId().equals(requester.getId()))
                .filter(candidate -> !candidate.getSongId().equals(requester.getSongId()))
                .filter(candidate -> candidate.getStartAt().isAfter(now))
                .filter(candidate -> !swapRequestRepository.existsPendingParticipation(List.of(candidate.getId())))
                .filter(candidate -> songMemberRepository.findBySongIdAndLeaderTrue(candidate.getSongId()).isPresent())
                .map(candidate -> new CandidateView(
                        candidate.getId(),
                        candidate.getSongId(),
                        songs.get(candidate.getSongId()).getTitle(),
                        candidate.getStartAt(),
                        candidate.getEndAt()
                ))
                .toList();
    }

    @Transactional
    public SwapView request(
            Long userId,
            Long requesterReservationId,
            Long targetReservationId
    ) {
        if (requesterReservationId == null || targetReservationId == null) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_RESERVATIONS_REQUIRED",
                    "교환할 두 예약이 필요합니다."
            );
        }
        if (requesterReservationId.equals(targetReservationId)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_SAME_RESERVATION",
                    "같은 예약끼리는 교환할 수 없습니다."
            );
        }

        ClubMember membership = membershipService.requireMembership(userId);
        LockedReservations locked = lockReservations(requesterReservationId, targetReservationId);
        Reservation requester = locked.byId(requesterReservationId);
        Reservation target = locked.byId(targetReservationId);
        Instant now = clock.instant();

        Song requesterSong = lockSongInClub(requester.getSongId(), membership.getClubId());
        Song targetSong = lockSongInClub(target.getSongId(), membership.getClubId());
        if (requesterSong.getId().equals(targetSong.getId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_SAME_TEAM",
                    "같은 팀의 예약끼리는 교환할 수 없습니다."
            );
        }

        requireLeader(userId, requesterSong.getId(), "교환 요청은 요청 팀의 현재 팀장만 만들 수 있습니다.");
        requireTeamSwapEligibleReservation(requester, requesterSong, now);
        requireTeamSwapEligibleReservation(target, targetSong, now);
        requireTargetLeader(targetSong.getId());

        if (swapRequestRepository.existsPendingParticipation(
                List.of(requester.getId(), target.getId())
        )) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_RESERVATION_ALREADY_PENDING",
                    "두 예약 중 하나가 이미 다른 교환 요청에 참여 중입니다."
            );
        }

        validateTeamMultipleReservationPolicy(
                membership.getClubId(),
                requester,
                target,
                now
        );
        validateAndLockPlan(membership.getClubId(), requester, target);

        SwapRequest saved = swapRequestRepository.saveAndFlush(SwapRequest.pending(
                requester,
                target,
                userId,
                now
        ));
        return toView(saved, membership.getClubId(), userId, false);
    }

    @Transactional
    public SwapView accept(Long userId, Long swapRequestId) {
        ClubMember membership = membershipService.requireMembership(userId);
        SwapContext context = lockSwapContext(swapRequestId);
        requirePending(context.request());
        ensureSwapClub(context, membership.getClubId());

        Song requesterSong = lockSongInClub(
                context.requester().getSongId(), membership.getClubId()
        );
        Song targetSong = lockSongInClub(
                context.target().getSongId(), membership.getClubId()
        );
        requireLeader(userId, targetSong.getId(), "교환 요청은 대상 팀의 현재 팀장만 수락할 수 있습니다.");

        Instant now = clock.instant();
        requireTeamSwapEligibleReservation(context.requester(), requesterSong, now);
        requireTeamSwapEligibleReservation(context.target(), targetSong, now);
        requireSnapshotMatch(context);
        validateTeamMultipleReservationPolicy(
                membership.getClubId(),
                context.requester(),
                context.target(),
                now
        );

        SwapPlan plan = validateAndLockPlan(
                membership.getClubId(),
                context.requester(),
                context.target()
        );

        context.request().accept(userId, now);
        swapRequestRepository.flush();
        applyPlan(plan, now);
        return toView(context.request(), membership.getClubId(), userId, false);
    }

    @Transactional
    public SwapView reject(Long userId, Long swapRequestId) {
        ClubMember membership = membershipService.requireMembership(userId);
        SwapContext context = lockSwapContext(swapRequestId);
        requirePending(context.request());
        ensureSwapClub(context, membership.getClubId());
        requireLeader(
                userId,
                context.target().getSongId(),
                "교환 요청은 대상 팀의 현재 팀장만 거절할 수 있습니다."
        );
        context.request().reject(userId, clock.instant());
        return toView(context.request(), membership.getClubId(), userId, false);
    }

    @Transactional
    public SwapView cancel(Long userId, Long swapRequestId) {
        ClubMember membership = membershipService.requireMembership(userId);
        SwapContext context = lockSwapContext(swapRequestId);
        requirePending(context.request());
        ensureSwapClub(context, membership.getClubId());
        requireLeader(
                userId,
                context.requester().getSongId(),
                "교환 요청은 요청 팀의 현재 팀장만 취소할 수 있습니다."
        );
        context.request().cancel(userId, clock.instant());
        return toView(context.request(), membership.getClubId(), userId, false);
    }

    @Transactional(readOnly = true)
    public List<SwapView> adminList(Long actorUserId, SwapRequestStatus status) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        List<SwapRequest> requests = status == null
                ? swapRequestRepository.findAllByOrderByRequestedAtDescIdDesc()
                : swapRequestRepository.findAllByStatusOrderByRequestedAtDescIdDesc(status);

        return requests.stream()
                .map(request -> toView(request, actor.getClubId(), actorUserId, true))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public SwapView adminAccept(Long actorUserId, Long swapRequestId, String reason) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        String normalizedReason = requireReason(reason);
        SwapContext context = lockSwapContext(swapRequestId);
        requirePending(context.request());
        ensureSwapClub(context, actor.getClubId());
        requireActive(context.requester());
        requireActive(context.target());
        requireSnapshotMatch(context);

        Map<String, Object> before = swapSnapshot(context.request(), context.requester(), context.target());
        SwapPlan plan = validateAndLockPlan(
                actor.getClubId(), context.requester(), context.target()
        );
        Instant now = clock.instant();
        context.request().accept(actorUserId, now);
        swapRequestRepository.flush();
        applyPlan(plan, now);

        actionLogService.record(
                actorUserId,
                "SWAP_ADMIN_ACCEPT",
                "SWAP_REQUEST",
                context.request().getId(),
                normalizedReason,
                before,
                swapSnapshot(context.request(), context.requester(), context.target())
        );
        return toView(context.request(), actor.getClubId(), actorUserId, true);
    }

    @Transactional
    public SwapView adminReject(Long actorUserId, Long swapRequestId, String reason) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        String normalizedReason = requireReason(reason);
        SwapContext context = lockSwapContext(swapRequestId);
        requirePending(context.request());
        ensureSwapClub(context, actor.getClubId());

        Map<String, Object> before = swapSnapshot(context.request(), context.requester(), context.target());
        context.request().reject(actorUserId, clock.instant());
        actionLogService.record(
                actorUserId,
                "SWAP_ADMIN_REJECT",
                "SWAP_REQUEST",
                context.request().getId(),
                normalizedReason,
                before,
                swapSnapshot(context.request(), context.requester(), context.target())
        );
        return toView(context.request(), actor.getClubId(), actorUserId, true);
    }

    @Transactional
    public SwapView adminDirect(
            Long actorUserId,
            Long firstReservationId,
            Long secondReservationId,
            String reason
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        String normalizedReason = requireReason(reason);
        if (firstReservationId == null || secondReservationId == null
                || firstReservationId.equals(secondReservationId)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_RESERVATIONS_REQUIRED",
                    "서로 다른 두 예약이 필요합니다."
            );
        }

        LockedReservations locked = lockReservations(firstReservationId, secondReservationId);
        Reservation first = locked.byId(firstReservationId);
        Reservation second = locked.byId(secondReservationId);
        Song firstSong = requireSongInClub(first.getSongId(), actor.getClubId());
        Song secondSong = requireSongInClub(second.getSongId(), actor.getClubId());
        if (firstSong.getId().equals(secondSong.getId())) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_SAME_TEAM",
                    "같은 팀의 예약끼리는 교환할 수 없습니다."
            );
        }
        requireActive(first);
        requireActive(second);

        Map<String, Object> before = directSwapSnapshot(first, second);
        SwapPlan plan = validateAndLockPlan(actor.getClubId(), first, second);
        Instant now = clock.instant();
        SwapRequest request = swapRequestRepository.saveAndFlush(
                SwapRequest.directAccepted(first, second, actorUserId, now)
        );
        applyPlan(plan, now);

        actionLogService.record(
                actorUserId,
                "SWAP_ADMIN_DIRECT",
                "SWAP_REQUEST",
                request.getId(),
                normalizedReason,
                before,
                swapSnapshot(request, first, second)
        );
        return toView(request, actor.getClubId(), actorUserId, true);
    }

    private SwapContext lockSwapContext(Long swapRequestId) {
        SwapRequest snapshot = swapRequestRepository.findById(swapRequestId)
                .orElseThrow(this::swapNotFound);
        LockedReservations locked = lockReservations(
                snapshot.getRequesterReservationId(),
                snapshot.getTargetReservationId()
        );
        SwapRequest lockedRequest = swapRequestRepository.findByIdForUpdate(swapRequestId)
                .orElseThrow(this::swapNotFound);
        return new SwapContext(
                lockedRequest,
                locked.byId(lockedRequest.getRequesterReservationId()),
                locked.byId(lockedRequest.getTargetReservationId())
        );
    }

    private LockedReservations lockReservations(Long firstId, Long secondId) {
        List<Long> ids = new ArrayList<>(List.of(firstId, secondId));
        ids.sort(Comparator.naturalOrder());
        Map<Long, Reservation> reservations = new LinkedHashMap<>();
        for (Long id : ids) {
            Reservation reservation = reservationRepository.findByIdForUpdate(id)
                    .orElseThrow(this::reservationNotFound);
            reservations.put(id, reservation);
        }
        return new LockedReservations(reservations);
    }

    private Song lockSongInClub(Long songId, Long clubId) {
        return songRepository.findForUpdate(songId, clubId)
                .orElseThrow(this::reservationNotFound);
    }

    private Song requireSongInClub(Long songId, Long clubId) {
        return songRepository.findByIdAndClubId(songId, clubId)
                .orElseThrow(this::reservationNotFound);
    }

    private void ensureSwapClub(SwapContext context, Long clubId) {
        requireSongInClub(context.requester().getSongId(), clubId);
        requireSongInClub(context.target().getSongId(), clubId);
    }

    private void requireTeamSwapEligibleReservation(
            Reservation reservation,
            Song song,
            Instant now
    ) {
        requireActive(reservation);
        if (!song.isActive()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_ARCHIVED",
                    "보관된 팀의 예약은 관리자만 교환할 수 있습니다."
            );
        }
        if (!now.isBefore(reservation.getStartAt())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_RESERVATION_ALREADY_STARTED",
                    "이미 시작한 합주는 팀장끼리 교환할 수 없습니다."
            );
        }
    }

    private void requireActive(Reservation reservation) {
        if (reservation.getStatus() != ReservationStatus.ACTIVE) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_NOT_ACTIVE",
                    "활성 상태인 예약만 교환할 수 있습니다."
            );
        }
    }

    private void requireLeader(Long userId, Long songId, String message) {
        boolean leader = songMemberRepository.findBySongIdAndUserId(songId, userId)
                .map(SongMember::isLeader)
                .orElse(false);
        if (!leader) {
            throw new AppException(HttpStatus.FORBIDDEN, "SONG_LEADER_REQUIRED", message);
        }
    }

    private void requireTargetLeader(Long songId) {
        if (songMemberRepository.findBySongIdAndLeaderTrue(songId).isEmpty()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_TARGET_LEADER_REQUIRED",
                    "대상 팀에 현재 팀장이 없어 교환 요청을 보낼 수 없습니다."
            );
        }
    }

    private Set<Long> leaderSongIds(Long userId) {
        return songMemberRepository.findAllByUserIdOrderByIdAsc(userId).stream()
                .filter(SongMember::isLeader)
                .map(SongMember::getSongId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private void validateTeamMultipleReservationPolicy(
            Long clubId,
            Reservation requester,
            Reservation target,
            Instant now
    ) {
        ReservationSettings settings = settingsRepository.findById(clubId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.CONFLICT,
                        "RESERVATION_SETTINGS_NOT_READY",
                        "예약 운영 설정이 준비되지 않았습니다."
                ));
        if (settings.isAllowMultipleReservations()) {
            return;
        }

        if (!requester.getBookingRoundId().equals(target.getBookingRoundId())) {
            boolean requesterConflict = reservationRepository
                    .findAllBySongIdInAndStatusAndEndAtAfterOrderByStartAtAsc(
                            List.of(requester.getSongId()),
                            ReservationStatus.ACTIVE,
                            now
                    ).stream()
                    .anyMatch(item -> !item.getId().equals(requester.getId())
                            && item.getBookingRoundId().equals(target.getBookingRoundId()));
            boolean targetConflict = reservationRepository
                    .findAllBySongIdInAndStatusAndEndAtAfterOrderByStartAtAsc(
                            List.of(target.getSongId()),
                            ReservationStatus.ACTIVE,
                            now
                    ).stream()
                    .anyMatch(item -> !item.getId().equals(target.getId())
                            && item.getBookingRoundId().equals(requester.getBookingRoundId()));
            if (requesterConflict || targetConflict) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "MULTIPLE_RESERVATIONS_NOT_ALLOWED",
                        "교환 후 같은 회차에 동일 팀 예약이 두 개 생길 수 없습니다."
                );
            }
        }
    }

    private void requireSnapshotMatch(SwapContext context) {
        if (!context.request().snapshotsMatch(context.requester(), context.target())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_RESERVATION_CHANGED",
                    "교환 요청 이후 예약 시간이 변경되어 더 이상 수락할 수 없습니다."
            );
        }
    }

    private SwapPlan validateAndLockPlan(
            Long clubId,
            Reservation requester,
            Reservation target
    ) {
        int requesterDuration = durationMinutes(requester);
        int targetDuration = durationMinutes(target);

        Instant requesterTargetStart = target.getStartAt();
        Instant requesterTargetEnd = requesterTargetStart.plusSeconds(requesterDuration * 60L);
        Instant targetTargetStart = requester.getStartAt();
        Instant targetTargetEnd = targetTargetStart.plusSeconds(targetDuration * 60L);

        if (rangesOverlap(
                requesterTargetStart,
                requesterTargetEnd,
                targetTargetStart,
                targetTargetEnd
        )) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_TARGETS_OVERLAP",
                    "각 팀의 원래 예약 길이를 유지하면 교환 후 시간이 서로 겹칩니다."
            );
        }

        LocalDate requesterTargetDate = requesterTargetStart
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();
        LocalDate targetTargetDate = targetTargetStart
                .atZone(ScheduleService.SERVICE_ZONE)
                .toLocalDate();

        validateRoomAndBlocked(
                clubId, requesterTargetDate, requesterTargetStart, requesterTargetEnd
        );
        validateRoomAndBlocked(
                clubId, targetTargetDate, targetTargetStart, targetTargetEnd
        );

        Instant firstWindowTo = later(requester.getEndAt(), targetTargetEnd);
        Instant secondWindowTo = later(target.getEndAt(), requesterTargetEnd);
        List<ReservationSlot> lockedSlots = slotRepository.findSwapWindowsForUpdate(
                requester.getBookingRoundId(),
                requester.getStartAt(),
                firstWindowTo,
                target.getBookingRoundId(),
                target.getStartAt(),
                secondWindowTo
        );

        List<ReservationSlot> requesterCurrent = slotsForRange(
                lockedSlots,
                requester.getBookingRoundId(),
                requester.getStartAt(),
                requester.getEndAt()
        );
        List<ReservationSlot> targetCurrent = slotsForRange(
                lockedSlots,
                target.getBookingRoundId(),
                target.getStartAt(),
                target.getEndAt()
        );
        List<ReservationSlot> requesterTarget = slotsForRange(
                lockedSlots,
                target.getBookingRoundId(),
                requesterTargetStart,
                requesterTargetEnd
        );
        List<ReservationSlot> targetTarget = slotsForRange(
                lockedSlots,
                requester.getBookingRoundId(),
                targetTargetStart,
                targetTargetEnd
        );

        validateExactSlots(
                requesterCurrent,
                requester.getStartAt(),
                requesterDuration,
                requester.getId()
        );
        validateExactSlots(
                targetCurrent,
                target.getStartAt(),
                targetDuration,
                target.getId()
        );
        validateExactSlots(
                requesterTarget,
                requesterTargetStart,
                requesterDuration,
                null
        );
        validateExactSlots(
                targetTarget,
                targetTargetStart,
                targetDuration,
                null
        );

        Set<Long> allowedReservationIds = Set.of(requester.getId(), target.getId());
        validateSwapTargetAvailability(requesterTarget, allowedReservationIds);
        validateSwapTargetAvailability(targetTarget, allowedReservationIds);

        return new SwapPlan(
                requester,
                target,
                requesterCurrent,
                targetCurrent,
                requesterTarget,
                targetTarget,
                target.getBookingRoundId(),
                requesterTargetStart,
                requesterTargetEnd,
                requester.getBookingRoundId(),
                targetTargetStart,
                targetTargetEnd
        );
    }

    private void applyPlan(SwapPlan plan, Instant now) {
        plan.requesterCurrent().forEach(ReservationSlot::release);
        plan.targetCurrent().forEach(ReservationSlot::release);
        plan.requesterTarget().forEach(slot -> slot.occupy(plan.requester().getId()));
        plan.targetTarget().forEach(slot -> slot.occupy(plan.target().getId()));
        plan.requester().relocate(
                plan.requesterTargetRoundId(),
                plan.requesterTargetStart(),
                plan.requesterTargetEnd(),
                now
        );
        plan.target().relocate(
                plan.targetTargetRoundId(),
                plan.targetTargetStart(),
                plan.targetTargetEnd(),
                now
        );
    }

    private void validateRoomAndBlocked(
            Long clubId,
            LocalDate date,
            Instant startAt,
            Instant endAt
    ) {
        var window = roomOperatingHoursPolicy.effective(clubId, date);
        if (!roomOperatingHoursPolicy.contains(date, startAt, endAt, window)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "SWAP_OUTSIDE_ROOM_HOURS",
                    "교환 후 예약 시간이 해당 날짜의 동아리방 운영시간을 벗어납니다."
            );
        }

        List<RoomException> blocked = exceptionRepository
                .findAllByClubIdAndExceptionDateOrderByBlockedStartMinuteAsc(clubId, date);
        int startMinute = roomOperatingHoursPolicy.minuteOffset(date, startAt);
        int endMinute = roomOperatingHoursPolicy.minuteOffset(date, endAt);
        if (blocked.stream().anyMatch(exception ->
                startMinute < exception.getBlockedEndMinute()
                        && endMinute > exception.getBlockedStartMinute())) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_ROOM_TIME_BLOCKED",
                    "교환 후 예약 시간이 동아리방 사용 불가 시간과 겹칩니다."
            );
        }
    }

    private List<ReservationSlot> slotsForRange(
            Collection<ReservationSlot> slots,
            Long bookingRoundId,
            Instant from,
            Instant to
    ) {
        return slots.stream()
                .filter(slot -> slot.getBookingRoundId().equals(bookingRoundId))
                .filter(slot -> !slot.getSlotStartAt().isBefore(from))
                .filter(slot -> slot.getSlotStartAt().isBefore(to))
                .sorted(Comparator.comparing(ReservationSlot::getSlotStartAt))
                .toList();
    }

    private void validateExactSlots(
            List<ReservationSlot> slots,
            Instant startAt,
            int durationMinutes,
            Long requiredReservationId
    ) {
        int expectedCount = durationMinutes / ScheduleService.SLOT_MINUTES;
        if (slots.size() != expectedCount) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_SLOT_NOT_READY",
                    "교환에 필요한 예약 슬롯이 완전히 준비되지 않았습니다."
            );
        }
        for (int index = 0; index < slots.size(); index++) {
            ReservationSlot slot = slots.get(index);
            Instant expectedStart = startAt.plusSeconds(
                    index * ScheduleService.SLOT_MINUTES * 60L
            );
            if (!slot.getSlotStartAt().equals(expectedStart)) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SWAP_SLOT_NOT_CONTIGUOUS",
                        "교환에 필요한 예약 슬롯이 연속되어 있지 않습니다."
                );
            }
            if (requiredReservationId != null
                    && !requiredReservationId.equals(slot.getReservationId())) {
                throw new AppException(
                        HttpStatus.CONFLICT,
                        "SWAP_RESERVATION_SLOT_CHANGED",
                        "교환 요청에 참여한 기존 예약 슬롯 상태가 변경되었습니다."
                );
            }
        }
    }

    private void validateSwapTargetAvailability(
            Collection<ReservationSlot> slots,
            Set<Long> allowedReservationIds
    ) {
        if (slots.stream().anyMatch(slot ->
                slot.getReservationId() != null
                        && !allowedReservationIds.contains(slot.getReservationId()))) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_TARGET_SLOT_UNAVAILABLE",
                    "교환 후 필요한 시간에 다른 예약이 포함되어 있습니다."
            );
        }
    }

    private boolean rangesOverlap(Instant aStart, Instant aEnd, Instant bStart, Instant bEnd) {
        return aStart.isBefore(bEnd) && aEnd.isAfter(bStart);
    }

    private Instant later(Instant first, Instant second) {
        return first.isAfter(second) ? first : second;
    }

    private int durationMinutes(Reservation reservation) {
        return Math.toIntExact(Duration.between(
                reservation.getStartAt(), reservation.getEndAt()
        ).toMinutes());
    }

    private void requirePending(SwapRequest request) {
        if (!request.isPending()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SWAP_REQUEST_NOT_PENDING",
                    "이미 처리되었거나 만료된 교환 요청입니다."
            );
        }
    }

    private String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_REASON_REQUIRED",
                    "관리자 작업 사유가 필요합니다."
            );
        }
        String normalized = reason.trim();
        if (normalized.length() > 500) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "ADMIN_REASON_TOO_LONG",
                    "관리자 작업 사유는 500자 이하여야 합니다."
            );
        }
        return normalized;
    }

    private SwapView toView(
            SwapRequest request,
            Long clubId,
            Long actorUserId,
            boolean admin
    ) {
        Reservation requester = reservationRepository.findById(request.getRequesterReservationId())
                .orElse(null);
        Reservation target = reservationRepository.findById(request.getTargetReservationId())
                .orElse(null);
        if (requester == null || target == null) {
            return null;
        }
        Song requesterSong = songRepository.findByIdAndClubId(requester.getSongId(), clubId)
                .orElse(null);
        Song targetSong = songRepository.findByIdAndClubId(target.getSongId(), clubId)
                .orElse(null);
        if (requesterSong == null || targetSong == null) {
            return null;
        }

        boolean canCancel = !admin
                && request.isPending()
                && isLeader(actorUserId, requesterSong.getId());
        boolean canRespond = !admin
                && request.isPending()
                && isLeader(actorUserId, targetSong.getId());

        return new SwapView(
                request.getId(),
                request.getStatus(),
                new ReservationSummary(
                        requester.getId(),
                        requesterSong.getId(),
                        requesterSong.getTitle(),
                        request.getRequesterStartSnapshot(),
                        request.getRequesterEndSnapshot()
                ),
                new ReservationSummary(
                        target.getId(),
                        targetSong.getId(),
                        targetSong.getTitle(),
                        request.getTargetStartSnapshot(),
                        request.getTargetEndSnapshot()
                ),
                request.getRequestedBy(),
                request.getRespondedBy(),
                request.getRequestedAt(),
                request.getRespondedAt(),
                request.getExpiredAt(),
                canRespond,
                canRespond,
                canCancel
        );
    }

    private boolean isLeader(Long userId, Long songId) {
        return songMemberRepository.findBySongIdAndUserId(songId, userId)
                .map(SongMember::isLeader)
                .orElse(false);
    }

    private Map<String, Object> swapSnapshot(
            SwapRequest request,
            Reservation requester,
            Reservation target
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("swapRequestId", request.getId());
        result.put("status", request.getStatus().name());
        result.put("requesterReservationId", requester.getId());
        result.put("requesterBookingRoundId", requester.getBookingRoundId());
        result.put("requesterStartAt", requester.getStartAt().toString());
        result.put("requesterEndAt", requester.getEndAt().toString());
        result.put("targetReservationId", target.getId());
        result.put("targetBookingRoundId", target.getBookingRoundId());
        result.put("targetStartAt", target.getStartAt().toString());
        result.put("targetEndAt", target.getEndAt().toString());
        return result;
    }

    private Map<String, Object> directSwapSnapshot(Reservation first, Reservation second) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("firstReservationId", first.getId());
        result.put("firstBookingRoundId", first.getBookingRoundId());
        result.put("firstStartAt", first.getStartAt().toString());
        result.put("firstEndAt", first.getEndAt().toString());
        result.put("secondReservationId", second.getId());
        result.put("secondBookingRoundId", second.getBookingRoundId());
        result.put("secondStartAt", second.getStartAt().toString());
        result.put("secondEndAt", second.getEndAt().toString());
        return result;
    }

    private AppException reservationNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "RESERVATION_NOT_FOUND",
                "예약을 찾을 수 없습니다."
        );
    }

    private AppException swapNotFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "SWAP_REQUEST_NOT_FOUND",
                "일정 교환 요청을 찾을 수 없습니다."
        );
    }

    public record CandidateView(
            Long reservationId,
            Long songId,
            String songTitle,
            Instant startAt,
            Instant endAt
    ) {
    }

    public record ReservationSummary(
            Long reservationId,
            Long songId,
            String songTitle,
            Instant startAt,
            Instant endAt
    ) {
    }

    public record SwapView(
            Long id,
            SwapRequestStatus status,
            ReservationSummary requester,
            ReservationSummary target,
            Long requestedBy,
            Long respondedBy,
            Instant requestedAt,
            Instant respondedAt,
            Instant expiredAt,
            boolean canAccept,
            boolean canReject,
            boolean canCancel
    ) {
    }

    private record LockedReservations(Map<Long, Reservation> reservations) {
        Reservation byId(Long id) {
            Reservation reservation = reservations.get(id);
            if (reservation == null) {
                throw new IllegalStateException("locked reservation missing: " + id);
            }
            return reservation;
        }
    }

    private record SwapContext(
            SwapRequest request,
            Reservation requester,
            Reservation target
    ) {
    }

    private record SwapPlan(
            Reservation requester,
            Reservation target,
            List<ReservationSlot> requesterCurrent,
            List<ReservationSlot> targetCurrent,
            List<ReservationSlot> requesterTarget,
            List<ReservationSlot> targetTarget,
            Long requesterTargetRoundId,
            Instant requesterTargetStart,
            Instant requesterTargetEnd,
            Long targetTargetRoundId,
            Instant targetTargetStart,
            Instant targetTargetEnd
    ) {
    }
}
