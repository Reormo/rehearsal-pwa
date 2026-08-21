package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_round_id", nullable = false)
    private Long bookingRoundId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationSource source;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "canceled_by")
    private Long canceledBy;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reservation() {
    }

    private Reservation(
            Long bookingRoundId,
            Long songId,
            Instant startAt,
            Instant endAt,
            ReservationSource source,
            Long createdBy,
            Instant now
    ) {
        this.bookingRoundId = bookingRoundId;
        this.songId = songId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = ReservationStatus.ACTIVE;
        this.source = source;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Reservation team(
            Long bookingRoundId,
            Long songId,
            Instant startAt,
            Instant endAt,
            Long createdBy,
            Instant now
    ) {
        return new Reservation(
                bookingRoundId,
                songId,
                startAt,
                endAt,
                ReservationSource.TEAM,
                createdBy,
                now
        );
    }

    public static Reservation admin(
            Long bookingRoundId,
            Long songId,
            Instant startAt,
            Instant endAt,
            Long createdBy,
            Instant now
    ) {
        return new Reservation(
                bookingRoundId,
                songId,
                startAt,
                endAt,
                ReservationSource.ADMIN,
                createdBy,
                now
        );
    }

    public void reschedule(Instant startAt, Instant endAt, Instant now) {
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    public void relocate(
            Long bookingRoundId,
            Instant startAt,
            Instant endAt,
            Instant now
    ) {
        this.bookingRoundId = bookingRoundId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.updatedAt = now;
    }

    public void cancel(Long canceledBy, String reason, Instant now) {
        if (status == ReservationStatus.CANCELED) {
            return;
        }
        this.status = ReservationStatus.CANCELED;
        this.canceledBy = canceledBy;
        this.cancellationReason = reason;
        this.canceledAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingRoundId() {
        return bookingRoundId;
    }

    public Long getSongId() {
        return songId;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public ReservationSource getSource() {
        return source;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Long getCanceledBy() {
        return canceledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public Instant getCanceledAt() {
        return canceledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
