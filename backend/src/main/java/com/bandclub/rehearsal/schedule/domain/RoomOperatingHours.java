package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "room_operating_hours")
public class RoomOperatingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "operating_date", nullable = false)
    private LocalDate operatingDate;

    @Column(name = "open_minute", nullable = false)
    private short openMinute;

    @Column(name = "close_minute", nullable = false)
    private short closeMinute;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomOperatingHours() {
    }

    private RoomOperatingHours(
            Long clubId,
            LocalDate operatingDate,
            int openMinute,
            int closeMinute,
            String reason,
            Long updatedBy,
            Instant now
    ) {
        this.clubId = clubId;
        this.operatingDate = operatingDate;
        this.openMinute = (short) openMinute;
        this.closeMinute = (short) closeMinute;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static RoomOperatingHours create(
            Long clubId,
            LocalDate operatingDate,
            int openMinute,
            int closeMinute,
            String reason,
            Long updatedBy,
            Instant now
    ) {
        return new RoomOperatingHours(
                clubId,
                operatingDate,
                openMinute,
                closeMinute,
                reason,
                updatedBy,
                now
        );
    }

    public void update(
            int openMinute,
            int closeMinute,
            String reason,
            Long updatedBy,
            Instant now
    ) {
        this.openMinute = (short) openMinute;
        this.closeMinute = (short) closeMinute;
        this.reason = reason;
        this.updatedBy = updatedBy;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public LocalDate getOperatingDate() {
        return operatingDate;
    }

    public int getOpenMinute() {
        return openMinute;
    }

    public int getCloseMinute() {
        return closeMinute;
    }

    public String getReason() {
        return reason;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
