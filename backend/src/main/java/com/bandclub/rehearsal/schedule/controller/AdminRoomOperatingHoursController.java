package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.service.AdminRoomOperatingHoursService;
import com.bandclub.rehearsal.schedule.service.RoomOperatingHoursPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin/schedule/operating-hours")
public class AdminRoomOperatingHoursController {

    private final AdminRoomOperatingHoursService service;

    public AdminRoomOperatingHoursController(AdminRoomOperatingHoursService service) {
        this.service = service;
    }

    @GetMapping("/{date}")
    public OperatingHoursResponse effective(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return OperatingHoursResponse.from(service.effective(userId(jwt), date));
    }

    @GetMapping
    public List<OperatingHoursResponse> overrides(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return service.overrides(userId(jwt), from, to).stream()
                .map(OperatingHoursResponse::from)
                .toList();
    }

    @PutMapping("/{date}")
    public UpdateResponse override(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody OverrideRequest request
    ) {
        return UpdateResponse.from(service.override(
                userId(jwt),
                date,
                request.openTime(),
                request.closeTime(),
                request.reason()
        ));
    }

    @PostMapping("/{date}/restore-default")
    public UpdateResponse restoreDefault(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody ReasonRequest request
    ) {
        return UpdateResponse.from(service.restoreDefault(
                userId(jwt),
                date,
                request.reason()
        ));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record OverrideRequest(
            @NotBlank String openTime,
            @NotBlank String closeTime,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ReasonRequest(
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record OperatingHoursResponse(
            LocalDate date,
            String openTime,
            String closeTime,
            boolean overridden,
            String reason
    ) {
        static OperatingHoursResponse from(
                AdminRoomOperatingHoursService.OperatingHoursView view
        ) {
            return new OperatingHoursResponse(
                    view.date(),
                    RoomOperatingHoursPolicy.formatBoundary(view.openMinute()),
                    RoomOperatingHoursPolicy.formatBoundary(view.closeMinute()),
                    view.overridden(),
                    view.reason()
            );
        }
    }

    public record UpdateResponse(
            OperatingHoursResponse operatingHours,
            List<Long> canceledReservationIds
    ) {
        static UpdateResponse from(AdminRoomOperatingHoursService.UpdateResult result) {
            return new UpdateResponse(
                    OperatingHoursResponse.from(result.operatingHours()),
                    result.canceledReservationIds()
            );
        }
    }
}
