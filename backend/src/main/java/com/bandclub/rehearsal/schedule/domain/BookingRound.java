package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "booking_rounds")
public class BookingRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "booking_open_at", nullable = false)
    private Instant bookingOpenAt;

    @Column(name = "booking_close_at", nullable = false)
    private Instant bookingCloseAt;

    @Column(name = "max_reservation_minutes", nullable = false)
    private short maxReservationMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BookingRound() {
    }

    private BookingRound(
            Long clubId,
            int roundNo,
            LocalDate startDate,
            LocalDate endDate,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Instant now
    ) {
        this.clubId = clubId;
        this.roundNo = roundNo;
        this.startDate = startDate;
        this.endDate = endDate;
        this.bookingOpenAt = bookingOpenAt;
        this.bookingCloseAt = bookingCloseAt;
        this.maxReservationMinutes = (short) maxReservationMinutes;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BookingRound create(
            Long clubId,
            int roundNo,
            LocalDate startDate,
            LocalDate endDate,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            Instant now
    ) {
        return new BookingRound(
                clubId,
                roundNo,
                startDate,
                endDate,
                bookingOpenAt,
                bookingCloseAt,
                maxReservationMinutes,
                now
        );
    }

    public void updatePolicy(Instant bookingOpenAt, int maxReservationMinutes, Instant now) {
        this.bookingOpenAt = bookingOpenAt;
        this.maxReservationMinutes = (short) maxReservationMinutes;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Instant getBookingOpenAt() {
        return bookingOpenAt;
    }

    public Instant getBookingCloseAt() {
        return bookingCloseAt;
    }

    public int getMaxReservationMinutes() {
        return maxReservationMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
