package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

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

    @Column(name = "blocked_start_time", nullable = false)
    private LocalTime blockedStartTime;

    @Column(name = "blocked_end_time", nullable = false)
    private LocalTime blockedEndTime;

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
            LocalTime blockedStartTime,
            LocalTime blockedEndTime,
            String reason,
            Long createdBy,
            Instant now
    ) {
        this.clubId = clubId;
        this.exceptionDate = exceptionDate;
        this.blockedStartTime = blockedStartTime;
        this.blockedEndTime = blockedEndTime;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static RoomException create(
            Long clubId,
            LocalDate exceptionDate,
            LocalTime blockedStartTime,
            LocalTime blockedEndTime,
            String reason,
            Long createdBy,
            Instant now
    ) {
        return new RoomException(
                clubId,
                exceptionDate,
                blockedStartTime,
                blockedEndTime,
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

    public LocalTime getBlockedStartTime() {
        return blockedStartTime;
    }

    public LocalTime getBlockedEndTime() {
        return blockedEndTime;
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
