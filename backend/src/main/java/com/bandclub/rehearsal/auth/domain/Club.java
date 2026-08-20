package com.bandclub.rehearsal.auth.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "clubs")
public class Club {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Club() {
    }

    private Club(String name, Instant now) {
        this.name = name;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Club create(String name, Instant now) {
        return new Club(name, now);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
