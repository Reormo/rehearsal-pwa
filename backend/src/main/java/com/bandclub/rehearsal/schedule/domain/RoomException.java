package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "room_exceptions")
public class RoomException {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "blocked_start_minute", nullable = false)
    private short blockedStartMinute;

    @Column(name = "blocked_end_minute", nullable = false)
    private short blockedEndMinute;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RoomException() {
    }

    private RoomException(
            Long clubId,
            LocalDate exceptionDate,
            int blockedStartMinute,
            int blockedEndMinute,
            String reason,
            Long createdBy,
            Instant now
    ) {
        this.clubId = clubId;
        this.exceptionDate = exceptionDate;
        this.blockedStartMinute = (short) blockedStartMinute;
        this.blockedEndMinute = (short) blockedEndMinute;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static RoomException create(
            Long clubId,
            LocalDate exceptionDate,
            int blockedStartMinute,
            int blockedEndMinute,
            String reason,
            Long createdBy,
            Instant now
    ) {
        return new RoomException(
                clubId,
                exceptionDate,
                blockedStartMinute,
                blockedEndMinute,
                reason,
                createdBy,
                now
        );
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public int getBlockedStartMinute() {
        return blockedStartMinute;
    }

    public int getBlockedEndMinute() {
        return blockedEndMinute;
    }

    public String getReason() {
        return reason;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
