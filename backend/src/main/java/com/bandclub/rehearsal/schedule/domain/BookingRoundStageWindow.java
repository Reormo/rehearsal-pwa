package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "booking_round_stage_windows")
public class BookingRoundStageWindow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_round_id", nullable = false)
    private Long bookingRoundId;

    @Column(name = "stage_type_id", nullable = false)
    private Long stageTypeId;

    @Column(name = "booking_open_at", nullable = false)
    private Instant bookingOpenAt;

    @Column(name = "booking_close_at", nullable = false)
    private Instant bookingCloseAt;

    @Column(name = "max_reservation_minutes", nullable = false)
    private short maxReservationMinutes;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingRoundStageWindow() {}

    private BookingRoundStageWindow(
            Long bookingRoundId,
            Long stageTypeId,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Long updatedBy,
            Instant now
    ) {
        this.bookingRoundId = bookingRoundId;
        this.stageTypeId = stageTypeId;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.maxReservationMinutes = (short) maxReservationMinutes;
        this.updatedBy = updatedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BookingRoundStageWindow create(
            Long bookingRoundId,
            Long stageTypeId,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Long updatedBy,
            Instant now
    ) {
        return new BookingRoundStageWindow(
                bookingRoundId,
                stageTypeId,
                bookingOpenAt,
                bookingCloseAt,
                maxReservationMinutes,
                updatedBy,
                now
        );
    }

    public void update(
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Long updatedBy,
            Instant now
    ) {
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.maxReservationMinutes = (short) maxReservationMinutes;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getBookingRoundId() { return bookingRoundId; }
    public Long getStageTypeId() { return stageTypeId; }
    public Instant getBookingOpenAt() { return bookingOpenAt; }
    public Instant getBookingCloseAt() { return bookingCloseAt; }
    public int getMaxReservationMinutes() { return maxReservationMinutes; }
    public Long getUpdatedBy() { return updatedBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
