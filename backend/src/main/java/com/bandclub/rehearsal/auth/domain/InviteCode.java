package com.bandclub.rehearsal.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "invite_codes")
public class InviteCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected InviteCode() {
    }

    private InviteCode(Long clubId, String code, Long createdBy, Instant now) {
        this.clubId = clubId;
        this.code = code;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public static InviteCode issue(Long clubId, String code, Long createdBy, Instant now) {
        return new InviteCode(clubId, code, createdBy, now);
    }

    public void revoke(Instant now) {
        if (this.revokedAt == null) {
            this.revokedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public String getCode() {
        return code;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
