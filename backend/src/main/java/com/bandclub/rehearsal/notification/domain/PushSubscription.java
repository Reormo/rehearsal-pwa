package com.bandclub.rehearsal.notification.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true, columnDefinition = "text")
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, columnDefinition = "text")
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, columnDefinition = "text")
    private String authKey;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected PushSubscription() {
    }

    private PushSubscription(
            Long userId,
            String endpoint,
            String p256dhKey,
            String authKey,
            String userAgent,
            Instant now
    ) {
        this.userId = userId;
        this.endpoint = endpoint;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.createdAt = now;
    }

    public static PushSubscription create(
            Long userId,
            String endpoint,
            String p256dhKey,
            String authKey,
            String userAgent,
            Instant now
    ) {
        return new PushSubscription(
                userId, endpoint, p256dhKey, authKey, userAgent, now
        );
    }

    public void refresh(
            Long userId,
            String p256dhKey,
            String authKey,
            String userAgent
    ) {
        this.userId = userId;
        this.p256dhKey = p256dhKey;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.disabledAt = null;
    }

    public void markSuccess(Instant now) {
        this.lastSuccessAt = now;
    }

    public void disable(Instant now) {
        if (disabledAt == null) {
            disabledAt = now;
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dhKey() { return p256dhKey; }
    public String getAuthKey() { return authKey; }
    public String getUserAgent() { return userAgent; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastSuccessAt() { return lastSuccessAt; }
    public Instant getDisabledAt() { return disabledAt; }
}
