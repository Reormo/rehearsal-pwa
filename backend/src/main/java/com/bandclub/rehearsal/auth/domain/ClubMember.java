package com.bandclub.rehearsal.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "club_members")
public class ClubMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ClubRole role;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ClubMember() {
    }

    private ClubMember(Long clubId, Long userId, ClubRole role, Instant now) {
        this.clubId = clubId;
        this.userId = userId;
        this.role = role;
        this.joinedAt = now;
        this.updatedAt = now;
    }

    public static ClubMember join(Long clubId, Long userId, ClubRole role, Instant now) {
        return new ClubMember(clubId, userId, role, now);
    }

    public void changeRole(ClubRole role, Instant now) {
        this.role = role;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public Long getUserId() {
        return userId;
    }

    public ClubRole getRole() {
        return role;
    }
}
