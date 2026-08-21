package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationSource;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.service.AdminReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin/reservations")
public class AdminReservationController {

    private final AdminReservationService adminReservationService;

    public AdminReservationController(AdminReservationService adminReservationService) {
        this.adminReservationService = adminReservationService;
    }

    @GetMapping
    public List<ReservationResponse> upcoming(@AuthenticationPrincipal Jwt jwt) {
        return adminReservationService.upcoming(userId(jwt)).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        return ReservationResponse.from(adminReservationService.create(
                userId(jwt),
                request.songId(),
                request.startAt(),
                request.durationMinutes(),
                request.reason()
        ));
    }

    @PatchMapping("/{reservationId}/move")
    public ReservationResponse move(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody MoveReservationRequest request
    ) {
        return ReservationResponse.from(adminReservationService.move(
                userId(jwt),
                reservationId,
                request.startAt(),
                request.reason()
        ));
    }

    @PatchMapping("/{reservationId}/extend")
    public ReservationResponse extend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody AdjustReservationRequest request
    ) {
        return ReservationResponse.from(adminReservationService.extend(
                userId(jwt),
                reservationId,
                request.boundary(),
                request.reason()
        ));
    }

    @PatchMapping("/{reservationId}/shorten")
    public ReservationResponse shorten(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody AdjustReservationRequest request
    ) {
        return ReservationResponse.from(adminReservationService.shorten(
                userId(jwt),
                reservationId,
                request.boundary(),
                request.reason()
        ));
    }

    @PostMapping("/{reservationId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody ReasonRequest request
    ) {
        adminReservationService.cancel(
                userId(jwt),
                reservationId,
                request.reason()
        );
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record CreateReservationRequest(
            @NotNull Long songId,
            @NotNull Instant startAt,
            @Min(30) @Max(180) int durationMinutes,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record MoveReservationRequest(
            @NotNull Instant startAt,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record AdjustReservationRequest(
            @NotNull ReservationBoundary boundary,
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ReasonRequest(
            @NotBlank @Size(max = 500) String reason
    ) {
    }

    public record ReservationResponse(
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
        static ReservationResponse from(AdminReservationService.ReservationView view) {
            return new ReservationResponse(
                    view.id(),
                    view.bookingRoundId(),
                    view.songId(),
                    view.songTitle(),
                    view.startAt(),
                    view.endAt(),
                    view.status(),
                    view.source(),
                    view.createdBy(),
                    view.canceledBy(),
                    view.cancellationReason(),
                    view.canceledAt(),
                    view.createdAt(),
                    view.updatedAt()
            );
        }
    }
}
