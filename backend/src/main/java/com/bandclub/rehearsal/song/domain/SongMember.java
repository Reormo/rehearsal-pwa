package com.bandclub.rehearsal.song.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "song_members")
public class SongMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_name", nullable = false, length = 50)
    private String sessionName;

    @Column(name = "is_leader", nullable = false)
    private boolean leader;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SongMember() {
    }

    private SongMember(Long songId, Long userId, String sessionName, boolean leader, Instant now) {
        this.songId = songId;
        this.userId = userId;
        this.sessionName = sessionName;
        this.leader = leader;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static SongMember join(Long songId, Long userId, String sessionName, boolean leader, Instant now) {
        return new SongMember(songId, userId, sessionName, leader, now);
    }

    public void changeSession(String sessionName, Instant now) {
        this.sessionName = sessionName;
        this.updatedAt = now;
    }

    public void appointLeader(Instant now) {
        this.leader = true;
        this.updatedAt = now;
    }

    public void releaseLeader(Instant now) {
        this.leader = false;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getSongId() {
        return songId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getSessionName() {
        return sessionName;
    }

    public boolean isLeader() {
        return leader;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
