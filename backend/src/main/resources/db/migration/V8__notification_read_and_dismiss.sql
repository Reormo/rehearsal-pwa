ALTER TABLE notifications
    ADD COLUMN dismissed_at TIMESTAMPTZ;

DROP INDEX IF EXISTS idx_notifications_user_unread_created;

CREATE INDEX idx_notifications_user_unread_created
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL AND dismissed_at IS NULL;

CREATE INDEX idx_notifications_user_visible_created
    ON notifications (user_id, created_at DESC)
    WHERE dismissed_at IS NULL;
