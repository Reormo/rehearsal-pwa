package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "reservation_settings")
public class ReservationSettings {

    @Id
    @Column(name = "club_id")
    private Long clubId;

    @Column(name = "allow_multiple_reservations", nullable = false)
    private boolean allowMultipleReservations;

    @Column(name = "default_booking_open_lead_minutes", nullable = false)
    private int defaultBookingOpenLeadMinutes;

    @Column(name = "default_max_reservation_minutes", nullable = false)
    private short defaultMaxReservationMinutes;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ReservationSettings() {
    }

    private ReservationSettings(
            Long clubId,
            boolean allowMultipleReservations,
            int defaultBookingOpenLeadMinutes,
            int defaultMaxReservationMinutes,
            Long updatedBy,
            Instant updatedAt
    ) {
        this.clubId = clubId;
        this.allowMultipleReservations = allowMultipleReservations;
        this.defaultBookingOpenLeadMinutes = defaultBookingOpenLeadMinutes;
        this.defaultMaxReservationMinutes = (short) defaultMaxReservationMinutes;
        this.updatedBy = updatedBy;
        this.updatedAt = updatedAt;
    }

    public static ReservationSettings initial(Long clubId, Long updatedBy, Instant now) {
        return new ReservationSettings(
                clubId,
                false,
                1680,
                90,
                updatedBy,
                now
        );
    }

    public void update(
            boolean allowMultipleReservations,
            int defaultBookingOpenLeadMinutes,
            int defaultMaxReservationMinutes,
            Long updatedBy,
            Instant now
    ) {
        this.allowMultipleReservations = allowMultipleReservations;
        this.defaultBookingOpenLeadMinutes = defaultBookingOpenLeadMinutes;
        this.defaultMaxReservationMinutes = (short) defaultMaxReservationMinutes;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long getClubId() {
        return clubId;
    }

    public boolean isAllowMultipleReservations() {
        return allowMultipleReservations;
    }

    public int getDefaultBookingOpenLeadMinutes() {
        return defaultBookingOpenLeadMinutes;
    }

    public int getDefaultMaxReservationMinutes() {
        return defaultMaxReservationMinutes;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
