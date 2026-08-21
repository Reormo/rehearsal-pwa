package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.service.RoomOperatingHoursPolicy;
import com.bandclub.rehearsal.schedule.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/calendar")
    public CalendarResponse calendar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return CalendarResponse.from(scheduleService.calendar(userId(jwt), from, to));
    }

    @GetMapping("/days/{date}")
    public DayScheduleResponse day(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return DayScheduleResponse.from(scheduleService.day(userId(jwt), date));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record CalendarResponse(
            LocalDate from,
            LocalDate to,
            List<DaySummaryResponse> days
    ) {
        static CalendarResponse from(ScheduleService.CalendarView view) {
            return new CalendarResponse(
                    view.from(),
                    view.to(),
                    view.days().stream().map(DaySummaryResponse::from).toList()
            );
        }
    }

    public record DaySummaryResponse(
            LocalDate date,
            Long roundId,
            Integer roundNo,
            ScheduleService.RoundState roundState,
            ScheduleService.RoomStatus roomStatus,
            OperatingHoursResponse operatingHours,
            int blockedPeriodCount
    ) {
        static DaySummaryResponse from(ScheduleService.DaySummary view) {
            return new DaySummaryResponse(
                    view.date(),
                    view.roundId(),
                    view.roundNo(),
                    view.roundState(),
                    view.roomStatus(),
                    OperatingHoursResponse.from(view.operatingHours()),
                    view.blockedPeriodCount()
            );
        }
    }

    public record DayScheduleResponse(
            LocalDate date,
            RoundResponse round,
            ScheduleService.RoomStatus roomStatus,
            OperatingHoursResponse operatingHours,
            List<AdminScheduleController.ExceptionResponse> blockedPeriods,
            List<BookableSlotResponse> standardSlots,
            List<BookableSlotResponse> remainderSlots,
            List<UnavailableSlotResponse> unavailableSlots
    ) {
        static DayScheduleResponse from(ScheduleService.DayScheduleView view) {
            return new DayScheduleResponse(
                    view.date(),
                    RoundResponse.from(view.round()),
                    view.roomStatus(),
                    OperatingHoursResponse.from(view.operatingHours()),
                    view.blockedPeriods().stream()
                            .map(AdminScheduleController.ExceptionResponse::from)
                            .toList(),
                    view.standardSlots().stream().map(BookableSlotResponse::from).toList(),
                    view.remainderSlots().stream().map(BookableSlotResponse::from).toList(),
                    view.unavailableSlots().stream().map(UnavailableSlotResponse::from).toList()
            );
        }
    }

    public record RoundResponse(
            Long id,
            int roundNo,
            LocalDate startDate,
            LocalDate endDate,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            ScheduleService.RoundState state
    ) {
        public static RoundResponse from(ScheduleService.RoundView view) {
            return new RoundResponse(
                    view.id(),
                    view.roundNo(),
                    view.startDate(),
                    view.endDate(),
                    view.bookingOpenAt(),
                    view.bookingCloseAt(),
                    view.maxReservationMinutes(),
                    view.state()
            );
        }
    }

    public record OperatingHoursResponse(
            String openTime,
            String closeTime,
            boolean overridden,
            String reason
    ) {
        static OperatingHoursResponse from(ScheduleService.OperatingHoursView view) {
            return new OperatingHoursResponse(
                    RoomOperatingHoursPolicy.formatBoundary(view.openMinute()),
                    RoomOperatingHoursPolicy.formatBoundary(view.closeMinute()),
                    view.overridden(),
                    view.reason()
            );
        }
    }

    public record BookableSlotResponse(
            Instant startAt,
            Instant endAt,
            int durationMinutes
    ) {
        static BookableSlotResponse from(ScheduleService.BookableSlotView view) {
            return new BookableSlotResponse(
                    view.startAt(),
                    view.endAt(),
                    view.durationMinutes()
            );
        }
    }

    public record UnavailableSlotResponse(
            Instant startAt,
            Instant endAt,
            ScheduleService.SlotState state,
            Long reservationId,
            Long songId,
            String songTitle
    ) {
        static UnavailableSlotResponse from(ScheduleService.UnavailableSlotView view) {
            return new UnavailableSlotResponse(
                    view.startAt(),
                    view.endAt(),
                    view.state(),
                    view.reservationId(),
                    view.songId(),
                    view.songTitle()
            );
        }
    }
}
