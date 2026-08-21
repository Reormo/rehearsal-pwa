CREATE TABLE user_notification_settings (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    rehearsal_reminder_minutes SMALLINT DEFAULT 30,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_user_notification_settings_reminder CHECK (
        rehearsal_reminder_minutes IS NULL
        OR rehearsal_reminder_minutes IN (10, 30, 60, 120, 1440)
    )
);

INSERT INTO user_notification_settings (
    user_id,
    rehearsal_reminder_minutes,
    updated_at
)
SELECT id, 30, CURRENT_TIMESTAMP
FROM users
ON CONFLICT (user_id) DO NOTHING;

CREATE OR REPLACE FUNCTION create_default_user_notification_settings()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO user_notification_settings (
        user_id,
        rehearsal_reminder_minutes,
        updated_at
    )
    VALUES (NEW.id, 30, CURRENT_TIMESTAMP)
    ON CONFLICT (user_id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_default_user_notification_settings
AFTER INSERT ON users
FOR EACH ROW
EXECUTE FUNCTION create_default_user_notification_settings();

CREATE TABLE push_subscriptions (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    endpoint TEXT NOT NULL UNIQUE,
    p256dh_key TEXT NOT NULL,
    auth_key TEXT NOT NULL,
    user_agent TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    last_success_at TIMESTAMPTZ,
    disabled_at TIMESTAMPTZ
);

CREATE INDEX idx_push_subscriptions_user_active
    ON push_subscriptions (user_id, id)
    WHERE disabled_at IS NULL;

CREATE OR REPLACE FUNCTION disable_deleted_user_push_subscriptions()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status <> 'DELETED' AND NEW.status = 'DELETED' THEN
        UPDATE push_subscriptions
        SET disabled_at = COALESCE(disabled_at, CURRENT_TIMESTAMP)
        WHERE user_id = NEW.id
          AND disabled_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_disable_deleted_user_push_subscriptions
AFTER UPDATE OF status ON users
FOR EACH ROW
EXECUTE FUNCTION disable_deleted_user_push_subscriptions();

CREATE OR REPLACE FUNCTION create_announcement_notifications()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO notifications (
        user_id, type, title, body, link_path, dedupe_key, created_at
    )
    SELECT
        cm.user_id,
        'ANNOUNCEMENT',
        '새 공지가 등록됐어요',
        NEW.title,
        '/announcements',
        'announcement:' || NEW.id || ':' || cm.user_id,
        NEW.created_at
    FROM club_members cm
    JOIN users u ON u.id = cm.user_id
    WHERE cm.club_id = NEW.club_id
      AND u.status = 'ACTIVE'
    ON CONFLICT (dedupe_key) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_announcement_notification
AFTER INSERT ON announcements
FOR EACH ROW
EXECUTE FUNCTION create_announcement_notifications();
