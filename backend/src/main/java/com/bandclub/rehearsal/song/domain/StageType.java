package com.bandclub.rehearsal.song.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "stage_types")
public class StageType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = 50)
    private String normalizedName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StageType() {}

    private StageType(Long clubId, String name, String normalizedName, Instant now) {
        this.clubId = clubId;
        this.name = name;
        this.normalizedName = normalizedName;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static StageType create(Long clubId, String name, String normalizedName, Instant now) {
        return new StageType(clubId, name, normalizedName, now);
    }

    public Long getId() { return id; }
    public Long getClubId() { return clubId; }
    public String getName() { return name; }
    public String getNormalizedName() { return normalizedName; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}