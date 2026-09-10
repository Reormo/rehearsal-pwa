package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.BookingRoundStageWindow;
import com.bandclub.rehearsal.schedule.repository.BookingRoundRepository;
import com.bandclub.rehearsal.schedule.repository.BookingRoundStageWindowRepository;
import com.bandclub.rehearsal.song.domain.StageType;
import com.bandclub.rehearsal.song.repository.StageTypeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class StageBookingWindowService {

    private static final Set<Integer> ALLOWED_MAX_MINUTES =
            Set.of(30, 60, 90, 120, 150, 180);

    private final MembershipService membershipService;
    private final BookingRoundRepository roundRepository;
    private final BookingRoundStageWindowRepository windowRepository;
    private final StageTypeRepository stageTypeRepository;
    private final AdminActionLogService actionLogService;
    private final Clock clock;

    public StageBookingWindowService(
            MembershipService membershipService,
            BookingRoundRepository roundRepository,
            BookingRoundStageWindowRepository windowRepository,
            StageTypeRepository stageTypeRepository,
            AdminActionLogService actionLogService,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.roundRepository = roundRepository;
        this.windowRepository = windowRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.actionLogService = actionLogService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StageWindowView> list(Long actorUserId, Long roundId) {
        var membership = membershipService.requireAdmin(actorUserId);
        BookingRound round = requireRound(roundId, membership.getClubId());

        return windowRepository
                .findAllByBookingRoundIdOrderByStageTypeIdAsc(round.getId())
                .stream()
                .map(window -> toView(
                        window,
                        requireStageType(
                                window.getStageTypeId(),
                                membership.getClubId()
                        )
                ))
                .toList();
    }

    @Transactional
    public StageWindowView upsert(
            Long actorUserId,
            Long roundId,
            Long stageTypeId,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes
    ) {
        var membership = membershipService.requireAdmin(actorUserId);
        BookingRound round = requireRound(roundId, membership.getClubId());
        StageType stageType = requireStageType(stageTypeId, membership.getClubId());
        validateWindow(round, bookingOpenAt, bookingCloseAt);
        validateMaxReservationMinutes(maxReservationMinutes);

        BookingRoundStageWindow existing = windowRepository
                .findByBookingRoundIdAndStageTypeId(roundId, stageTypeId)
                .orElse(null);

        Map<String, Object> before =
                existing == null ? null : snapshot(existing, stageType);
        Instant now = clock.instant();

        BookingRoundStageWindow saved;
        if (existing == null) {
            saved = windowRepository.save(BookingRoundStageWindow.create(
                    roundId,
                    stageTypeId,
                    bookingOpenAt,
                    bookingCloseAt,
                    maxReservationMinutes,
                    actorUserId,
                    now
            ));
        } else {
            existing.update(
                    bookingOpenAt,
                    bookingCloseAt,
                    maxReservationMinutes,
                    actorUserId,
                    now
            );
            saved = existing;
        }

        actionLogService.record(
                actorUserId,
                "BOOKING_STAGE_WINDOW_UPSERT",
                "BOOKING_ROUND_STAGE_WINDOW",
                saved.getId(),
                stageType.getName(),
                before,
                snapshot(saved, stageType)
        );

        return toView(saved, stageType);
    }

    @Transactional
    public void delete(
            Long actorUserId,
            Long roundId,
            Long stageTypeId
    ) {
        var membership = membershipService.requireAdmin(actorUserId);
        requireRound(roundId, membership.getClubId());
        StageType stageType = requireStageType(stageTypeId, membership.getClubId());

        BookingRoundStageWindow window = windowRepository
                .findByBookingRoundIdAndStageTypeId(roundId, stageTypeId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "STAGE_BOOKING_WINDOW_NOT_FOUND",
                        "해당 무대 종류의 별도 예약 정책이 없습니다."
                ));

        Map<String, Object> before = snapshot(window, stageType);
        Long id = window.getId();
        windowRepository.delete(window);

        actionLogService.record(
                actorUserId,
                "BOOKING_STAGE_WINDOW_DELETE",
                "BOOKING_ROUND_STAGE_WINDOW",
                id,
                stageType.getName(),
                before,
                null
        );
    }

    private BookingRound requireRound(Long roundId, Long clubId) {
        return roundRepository.findByIdAndClubId(roundId, clubId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "BOOKING_ROUND_NOT_FOUND",
                        "예약 회차를 찾을 수 없습니다."
                ));
    }

    private StageType requireStageType(Long stageTypeId, Long clubId) {
        return stageTypeRepository.findByIdAndClubId(stageTypeId, clubId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "STAGE_TYPE_NOT_FOUND",
                        "무대 종류를 찾을 수 없습니다."
                ));
    }

    private void validateWindow(
            BookingRound round,
            Instant bookingOpenAt,
            Instant bookingCloseAt
    ) {
        if (bookingOpenAt == null
                || bookingCloseAt == null
                || !bookingOpenAt.isBefore(bookingCloseAt)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STAGE_BOOKING_WINDOW",
                    "무대별 예약 오픈 시각은 종료 시각보다 빨라야 합니다."
            );
        }

        Instant roundEndLimit = round.getEndDate()
                .atTime(ScheduleService.DEFAULT_CLOSE_TIME)
                .atZone(ScheduleService.SERVICE_ZONE)
                .toInstant();

        if (bookingCloseAt.isAfter(roundEndLimit)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "STAGE_BOOKING_WINDOW_AFTER_ROUND",
                    "무대별 예약 종료 시각은 해당 회차 마지막 날 22:00보다 늦을 수 없습니다."
            );
        }
    }

    private void validateMaxReservationMinutes(int maxReservationMinutes) {
        if (!ALLOWED_MAX_MINUTES.contains(maxReservationMinutes)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_STAGE_MAX_RESERVATION_MINUTES",
                    "무대별 최대 예약 시간은 30, 60, 90, 120, 150, 180분 중 하나여야 합니다."
            );
        }
    }

    private StageWindowView toView(
            BookingRoundStageWindow window,
            StageType stageType
    ) {
        return new StageWindowView(
                window.getId(),
                window.getBookingRoundId(),
                stageType.getId(),
                stageType.getName(),
                window.getBookingOpenAt(),
                window.getBookingCloseAt(),
                window.getMaxReservationMinutes(),
                window.getUpdatedBy(),
                window.getUpdatedAt()
        );
    }

    private Map<String, Object> snapshot(
            BookingRoundStageWindow window,
            StageType stageType
    ) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bookingRoundId", window.getBookingRoundId());
        result.put("stageTypeId", stageType.getId());
        result.put("stageTypeName", stageType.getName());
        result.put("bookingOpenAt", window.getBookingOpenAt().toString());
        result.put("bookingCloseAt", window.getBookingCloseAt().toString());
        result.put("maxReservationMinutes", window.getMaxReservationMinutes());
        return result;
    }

    public record StageWindowView(
            Long id,
            Long bookingRoundId,
            Long stageTypeId,
            String stageTypeName,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Long updatedBy,
            Instant updatedAt
    ) {}
}
