package com.bandclub.rehearsal.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "link_path", length = 500)
    private String linkPath;

    @Column(name = "dedupe_key", length = 200)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            readAt = now;
        }
    }

    public void dismiss(Instant now) {
        markRead(now);
        if (dismissedAt == null) {
            dismissedAt = now;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLinkPath() { return linkPath; }
    public String getDedupeKey() { return dedupeKey; }
    public Instant getReadAt() { return readAt; }
    public Instant getDismissedAt() { return dismissedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
