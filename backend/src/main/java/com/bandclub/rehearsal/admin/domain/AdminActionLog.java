package com.bandclub.rehearsal.admin.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "admin_action_logs")
public class AdminActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(length = 500)
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_data", columnDefinition = "jsonb")
    private Map<String, Object> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_data", columnDefinition = "jsonb")
    private Map<String, Object> afterData;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AdminActionLog() {
    }

    private AdminActionLog(
            Long clubId,
            Long actorUserId,
            String actionType,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Instant createdAt
    ) {
        this.clubId = clubId;
        this.actorUserId = actorUserId;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.createdAt = createdAt;
    }

    public static AdminActionLog create(
            Long clubId,
            Long actorUserId,
            String actionType,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Instant createdAt
    ) {
        return new AdminActionLog(
                clubId,
                actorUserId,
                actionType,
                targetType,
                targetId,
                reason,
                beforeData,
                afterData,
                createdAt
        );
    }

    public Long getId() {
        return id;
    }

    public Long getClubId() {
        return clubId;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public String getActionType() {
        return actionType;
    }

    public String getTargetType() {
        return targetType;
    }

    public Long getTargetId() {
        return targetId;
    }

    public String getReason() {
        return reason;
    }

    public Map<String, Object> getBeforeData() {
        return beforeData;
    }

    public Map<String, Object> getAfterData() {
        return afterData;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
