package com.bandclub.rehearsal.schedule.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "swap_requests")
public class SwapRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_reservation_id", nullable = false)
    private Long requesterReservationId;

    @Column(name = "target_reservation_id", nullable = false)
    private Long targetReservationId;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Column(name = "responded_by")
    private Long respondedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SwapRequestStatus status;

    @Column(name = "requester_start_snapshot", nullable = false)
    private Instant requesterStartSnapshot;

    @Column(name = "requester_end_snapshot", nullable = false)
    private Instant requesterEndSnapshot;

    @Column(name = "target_start_snapshot", nullable = false)
    private Instant targetStartSnapshot;

    @Column(name = "target_end_snapshot", nullable = false)
    private Instant targetEndSnapshot;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    protected SwapRequest() {
    }

    private SwapRequest(
            Long requesterReservationId,
            Long targetReservationId,
            Long requestedBy,
            Long respondedBy,
            SwapRequestStatus status,
            Instant requesterStartSnapshot,
            Instant requesterEndSnapshot,
            Instant targetStartSnapshot,
            Instant targetEndSnapshot,
            Instant requestedAt,
            Instant respondedAt
    ) {
        this.requesterReservationId = requesterReservationId;
        this.targetReservationId = targetReservationId;
        this.requestedBy = requestedBy;
        this.respondedBy = respondedBy;
        this.status = status;
        this.requesterStartSnapshot = requesterStartSnapshot;
        this.requesterEndSnapshot = requesterEndSnapshot;
        this.targetStartSnapshot = targetStartSnapshot;
        this.targetEndSnapshot = targetEndSnapshot;
        this.requestedAt = requestedAt;
        this.respondedAt = respondedAt;
    }

    public static SwapRequest pending(
            Reservation requester,
            Reservation target,
            Long requestedBy,
            Instant now
    ) {
        return new SwapRequest(
                requester.getId(),
                target.getId(),
                requestedBy,
                null,
                SwapRequestStatus.PENDING,
                requester.getStartAt(),
                requester.getEndAt(),
                target.getStartAt(),
                target.getEndAt(),
                now,
                null
        );
    }

    public static SwapRequest directAccepted(
            Reservation first,
            Reservation second,
            Long actorUserId,
            Instant now
    ) {
        return new SwapRequest(
                first.getId(),
                second.getId(),
                actorUserId,
                actorUserId,
                SwapRequestStatus.ACCEPTED,
                first.getStartAt(),
                first.getEndAt(),
                second.getStartAt(),
                second.getEndAt(),
                now,
                now
        );
    }

    public void accept(Long respondedBy, Instant now) {
        requirePending();
        this.status = SwapRequestStatus.ACCEPTED;
        this.respondedBy = respondedBy;
        this.respondedAt = now;
    }

    public void reject(Long respondedBy, Instant now) {
        requirePending();
        this.status = SwapRequestStatus.REJECTED;
        this.respondedBy = respondedBy;
        this.respondedAt = now;
    }

    public void cancel(Long respondedBy, Instant now) {
        requirePending();
        this.status = SwapRequestStatus.CANCELED;
        this.respondedBy = respondedBy;
        this.respondedAt = now;
    }

    public boolean snapshotsMatch(Reservation requester, Reservation target) {
        return requesterStartSnapshot.equals(requester.getStartAt())
                && requesterEndSnapshot.equals(requester.getEndAt())
                && targetStartSnapshot.equals(target.getStartAt())
                && targetEndSnapshot.equals(target.getEndAt());
    }

    public boolean isPending() {
        return status == SwapRequestStatus.PENDING;
    }

    private void requirePending() {
        if (!isPending()) {
            throw new IllegalStateException("swap request is not pending");
        }
    }

    public Long getId() { return id; }
    public Long getRequesterReservationId() { return requesterReservationId; }
    public Long getTargetReservationId() { return targetReservationId; }
    public Long getRequestedBy() { return requestedBy; }
    public Long getRespondedBy() { return respondedBy; }
    public SwapRequestStatus getStatus() { return status; }
    public Instant getRequesterStartSnapshot() { return requesterStartSnapshot; }
    public Instant getRequesterEndSnapshot() { return requesterEndSnapshot; }
    public Instant getTargetStartSnapshot() { return targetStartSnapshot; }
    public Instant getTargetEndSnapshot() { return targetEndSnapshot; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
    public Instant getExpiredAt() { return expiredAt; }
}
