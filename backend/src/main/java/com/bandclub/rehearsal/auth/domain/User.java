package com.bandclub.rehearsal.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "users")
public class User {

    public static final String DELETED_USER_NAME = "삭제된 사용자";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", length = 50)
    private String loginId;

    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
    }

    private User(String loginId, String passwordHash, String name, Instant now) {
        this.loginId = loginId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.status = UserStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User active(String loginId, String passwordHash, String name, Instant now) {
        return new User(loginId, passwordHash, name, now);
    }

    public void changePasswordHash(String passwordHash, Instant now) {
        if (!isActive()) {
            throw new IllegalStateException("Deleted user cannot change password.");
        }
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void anonymize(Instant now) {
        this.loginId = null;
        this.passwordHash = null;
        this.name = DELETED_USER_NAME;
        this.status = UserStatus.DELETED;
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
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

    public UserStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE && deletedAt == null;
    }
}
