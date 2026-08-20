package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.service.ScheduleService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/schedule")
public class AdminScheduleController {

    private final ScheduleService scheduleService;

    public AdminScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping("/settings")
    public SettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        return SettingsResponse.from(scheduleService.adminSettings(userId(jwt)));
    }

    @PatchMapping("/settings")
    public SettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateSettingsRequest request
    ) {
        return SettingsResponse.from(scheduleService.updateSettings(
                userId(jwt),
                request.allowMultipleReservations(),
                request.defaultBookingOpenLeadMinutes(),
                request.defaultMaxReservationMinutes()
        ));
    }

    @GetMapping("/rounds")
    public List<ScheduleController.RoundResponse> rounds(@AuthenticationPrincipal Jwt jwt) {
        return scheduleService.adminRounds(userId(jwt)).stream()
                .map(ScheduleController.RoundResponse::from)
                .toList();
    }

    @PatchMapping("/rounds/{roundId}")
    public ScheduleController.RoundResponse updateRound(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long roundId,
            @Valid @RequestBody UpdateRoundRequest request
    ) {
        return ScheduleController.RoundResponse.from(scheduleService.updateRound(
                userId(jwt),
                roundId,
                request.bookingOpenAt(),
                request.maxReservationMinutes()
        ));
    }

    @GetMapping("/exceptions")
    public List<ExceptionResponse> exceptions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return scheduleService.adminExceptions(userId(jwt), from, to).stream()
                .map(ExceptionResponse::from)
                .toList();
    }

    @PostMapping("/exceptions")
    public ExceptionResponse createException(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateExceptionRequest request
    ) {
        return ExceptionResponse.from(scheduleService.createException(
                userId(jwt),
                request.date(),
                request.blockedStartTime(),
                request.blockedEndTime(),
                request.reason()
        ));
    }

    @DeleteMapping("/exceptions/{exceptionId}")
    public ResponseEntity<Void> deleteException(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long exceptionId
    ) {
        scheduleService.deleteException(userId(jwt), exceptionId);
        return ResponseEntity.noContent().build();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record UpdateSettingsRequest(
            boolean allowMultipleReservations,
            @Min(0) @Max(10080) int defaultBookingOpenLeadMinutes,
            @Min(30) @Max(180) int defaultMaxReservationMinutes
    ) {
    }

    public record UpdateRoundRequest(
            @NotNull Instant bookingOpenAt,
            @Min(30) @Max(180) int maxReservationMinutes
    ) {
    }

    public record CreateExceptionRequest(
            @NotNull LocalDate date,
            @NotNull LocalTime blockedStartTime,
            @NotNull LocalTime blockedEndTime,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record SettingsResponse(
            boolean allowMultipleReservations,
            int defaultBookingOpenLeadMinutes,
            int defaultMaxReservationMinutes,
            Long updatedBy,
            Instant updatedAt
    ) {
        static SettingsResponse from(ScheduleService.SettingsView view) {
            return new SettingsResponse(
                    view.allowMultipleReservations(),
                    view.defaultBookingOpenLeadMinutes(),
                    view.defaultMaxReservationMinutes(),
                    view.updatedBy(),
                    view.updatedAt()
            );
        }
    }

    public record ExceptionResponse(
            Long id,
            LocalDate date,
            LocalTime blockedStartTime,
            LocalTime blockedEndTime,
            String reason,
            Long createdBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        static ExceptionResponse from(ScheduleService.ExceptionView view) {
            return new ExceptionResponse(
                    view.id(),
                    view.date(),
                    view.blockedStartTime(),
                    view.blockedEndTime(),
                    view.reason(),
                    view.createdBy(),
                    view.createdAt(),
                    view.updatedAt()
            );
        }
    }
}
