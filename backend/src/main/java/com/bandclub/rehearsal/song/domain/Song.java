package com.bandclub.rehearsal.song.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "songs")
public class Song {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SongStatus status;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Song() {
    }

    private Song(Long clubId, String title, Long createdBy, Instant now) {
        this.clubId = clubId;
        this.title = title;
        this.status = SongStatus.ACTIVE;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Song active(Long clubId, String title, Long createdBy, Instant now) {
        return new Song(clubId, title, createdBy, now);
    }

    public void rename(String title, Instant now) {
        this.title = title;
        this.updatedAt = now;
    }

    public void archive(Instant now) {
        if (status == SongStatus.ARCHIVED) {
            return;
        }
        this.status = SongStatus.ARCHIVED;
        this.archivedAt = now;
        this.updatedAt = now;
    }

    public void restore(Instant now) {
        if (status == SongStatus.ACTIVE) {
            return;
        }
        this.status = SongStatus.ACTIVE;
        this.archivedAt = null;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public String getTitle() {
        return title;
    }

    public SongStatus getStatus() {
        return status;
    }

    public Instant getArchivedAt() {
        return archivedAt;
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

    public boolean isActive() {
        return status == SongStatus.ACTIVE;
    }
}
