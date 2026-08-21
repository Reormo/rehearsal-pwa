package com.bandclub.rehearsal.schedule.controller;

import com.bandclub.rehearsal.schedule.domain.ReservationBoundary;
import com.bandclub.rehearsal.schedule.domain.ReservationSource;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import com.bandclub.rehearsal.schedule.service.BookingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/options")
    public BookingOptionsResponse options(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam @Min(30) @Max(180) int durationMinutes
    ) {
        return BookingOptionsResponse.from(bookingService.options(
                userId(jwt),
                date,
                durationMinutes
        ));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateReservationRequest request
    ) {
        return ReservationResponse.from(bookingService.create(
                userId(jwt),
                request.songId(),
                request.startAt(),
                request.durationMinutes()
        ));
    }

    @GetMapping("/mine")
    public List<ReservationResponse> mine(@AuthenticationPrincipal Jwt jwt) {
        return bookingService.myUpcoming(userId(jwt)).stream()
                .map(ReservationResponse::from)
                .toList();
    }

    @PatchMapping("/{reservationId}/move")
    public ReservationResponse move(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody MoveReservationRequest request
    ) {
        return ReservationResponse.from(bookingService.move(
                userId(jwt),
                reservationId,
                request.startAt()
        ));
    }

    @PatchMapping("/{reservationId}/extend")
    public ReservationResponse extend(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody AdjustReservationRequest request
    ) {
        return ReservationResponse.from(bookingService.extend(
                userId(jwt),
                reservationId,
                request.boundary()
        ));
    }

    @PatchMapping("/{reservationId}/shorten")
    public ReservationResponse shorten(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId,
            @Valid @RequestBody AdjustReservationRequest request
    ) {
        return ReservationResponse.from(bookingService.shorten(
                userId(jwt),
                reservationId,
                request.boundary()
        ));
    }

    @DeleteMapping("/{reservationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long reservationId
    ) {
        bookingService.cancel(userId(jwt), reservationId);
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record CreateReservationRequest(
            @NotNull Long songId,
            @NotNull Instant startAt,
            @Min(30) @Max(180) int durationMinutes
    ) {
    }

    public record MoveReservationRequest(
            @NotNull Instant startAt
    ) {
    }

    public record AdjustReservationRequest(
            @NotNull ReservationBoundary boundary
    ) {
    }

    public record BookingTimeOptionResponse(
            Instant startAt,
            Instant endAt
    ) {
        static BookingTimeOptionResponse from(BookingService.BookingTimeOptionView view) {
            return new BookingTimeOptionResponse(view.startAt(), view.endAt());
        }
    }

    public record BookingOptionsResponse(
            LocalDate date,
            int durationMinutes,
            int maxReservationMinutes,
            boolean acceptingReservations,
            List<BookingTimeOptionResponse> options
    ) {
        static BookingOptionsResponse from(BookingService.BookingOptionsView view) {
            return new BookingOptionsResponse(
                    view.date(),
                    view.durationMinutes(),
                    view.maxReservationMinutes(),
                    view.acceptingReservations(),
                    view.options().stream().map(BookingTimeOptionResponse::from).toList()
            );
        }
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
        static ReservationResponse from(BookingService.ReservationView view) {
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
