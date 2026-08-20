CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    author_user_id BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_announcements_club_active
    ON announcements (club_id, is_pinned DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE admin_action_logs (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    actor_user_id BIGINT REFERENCES users(id),
    action_type VARCHAR(50) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT,
    reason VARCHAR(500),
    before_data JSONB,
    after_data JSONB,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_action_logs_club_created
    ON admin_action_logs (club_id, created_at DESC);

CREATE INDEX idx_admin_action_logs_actor_created
    ON admin_action_logs (actor_user_id, created_at DESC);
