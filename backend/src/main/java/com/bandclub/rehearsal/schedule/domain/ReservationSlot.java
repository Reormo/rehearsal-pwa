package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservation_slots")
public class ReservationSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "booking_round_id", nullable = false)
    private Long bookingRoundId;

    @Column(name = "slot_start_at", nullable = false)
    private Instant slotStartAt;

    @Column(name = "reservation_id")
    private Long reservationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReservationSlot() {
    }

    private ReservationSlot(Long bookingRoundId, Instant slotStartAt, Instant createdAt) {
        this.bookingRoundId = bookingRoundId;
        this.slotStartAt = slotStartAt;
        this.createdAt = createdAt;
    }

    public static ReservationSlot empty(Long bookingRoundId, Instant slotStartAt, Instant now) {
        return new ReservationSlot(bookingRoundId, slotStartAt, now);
    }

    public void occupy(Long reservationId) {
        if (reservationId == null) {
            throw new IllegalArgumentException("reservationId must not be null");
        }
        this.reservationId = reservationId;
    }

    public void release() {
        this.reservationId = null;
    }

    public Long getId() {
        return id;
    }

    public Long getBookingRoundId() {
        return bookingRoundId;
    }

    public Instant getSlotStartAt() {
        return slotStartAt;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
