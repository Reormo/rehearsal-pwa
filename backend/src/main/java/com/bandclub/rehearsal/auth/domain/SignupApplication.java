package com.bandclub.rehearsal.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "signup_applications")
public class SignupApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "invite_code_id", nullable = false)
    private Long inviteCodeId;

    @Column(name = "login_id", nullable = false, length = 50)
    private String loginId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SignupStatus status;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "approved_user_id")
    private Long approvedUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SignupApplication() {
    }

    private SignupApplication(
            Long clubId,
            Long inviteCodeId,
            String loginId,
            String passwordHash,
            String name,
            Instant now
    ) {
        this.clubId = clubId;
        this.inviteCodeId = inviteCodeId;
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.status = SignupStatus.PENDING;
        this.createdAt = now;
    }

    public static SignupApplication pending(
            Long clubId,
            Long inviteCodeId,
            String loginId,
            String passwordHash,
            String name,
            Instant now
    ) {
        return new SignupApplication(clubId, inviteCodeId, loginId, passwordHash, name, now);
    }

    public void approve(Long reviewerId, Long userId, Instant now) {
        ensurePending();
        this.status = SignupStatus.APPROVED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.approvedUserId = userId;
        this.rejectionReason = null;
        this.passwordHash = null;
    }

    public void reject(Long reviewerId, String reason, Instant now) {
        ensurePending();
        this.status = SignupStatus.REJECTED;
        this.reviewedBy = reviewerId;
        this.reviewedAt = now;
        this.rejectionReason = reason;
        this.passwordHash = null;
    }

    private void ensurePending() {
        if (status != SignupStatus.PENDING) {
            throw new IllegalStateException("Signup application is already reviewed.");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public SignupStatus getStatus() {
        return status;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
