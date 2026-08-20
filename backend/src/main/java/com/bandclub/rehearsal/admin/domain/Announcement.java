package com.bandclub.rehearsal.admin.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "announcements")
public class Announcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "is_pinned", nullable = false)
    private boolean pinned;

    @Column(name = "author_user_id")
    private Long authorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Announcement() {
    }

    private Announcement(Long clubId, String title, String content, boolean pinned, Long authorUserId, Instant now) {
        this.clubId = clubId;
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.authorUserId = authorUserId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Announcement create(
            Long clubId,
            String title,
            String content,
            boolean pinned,
            Long authorUserId,
            Instant now
    ) {
        return new Announcement(clubId, title, content, pinned, authorUserId, now);
    }

    public void update(String title, String content, boolean pinned, Instant now) {
        this.title = title;
        this.content = content;
        this.pinned = pinned;
        this.updatedAt = now;
    }

    public void delete(Instant now) {
        this.deletedAt = now;
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

    public String getContent() {
        return content;
    }

    public boolean isPinned() {
        return pinned;
    }

    public Long getAuthorUserId() {
        return authorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
