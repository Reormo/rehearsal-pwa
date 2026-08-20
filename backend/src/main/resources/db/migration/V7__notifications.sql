CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id),
    type VARCHAR(50) NOT NULL,
    title VARCHAR(150) NOT NULL,
    body TEXT NOT NULL,
    link_path VARCHAR(500),
    dedupe_key VARCHAR(200),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_notifications_dedupe_key UNIQUE (dedupe_key)
);

CREATE INDEX idx_notifications_user_unread_created
    ON notifications (user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE OR REPLACE FUNCTION create_reservation_canceled_notifications()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'ACTIVE' AND NEW.status = 'CANCELED' THEN
        INSERT INTO notifications (
            user_id,
            type,
            title,
            body,
            link_path,
            dedupe_key,
            created_at
        )
        SELECT
            sm.user_id,
            'RESERVATION_CANCELED',
            '합주 예약이 취소됐어요',
            s.title || ' · ' ||
                to_char(NEW.start_at AT TIME ZONE 'Asia/Seoul', 'FMMM"월" FMDD"일" HH24:MI') ||
                '~' || to_char(NEW.end_at AT TIME ZONE 'Asia/Seoul', 'HH24:MI') ||
                ' 예약이 취소되었습니다.' ||
                CASE
                    WHEN NEW.cancellation_reason IS NULL OR NEW.cancellation_reason = '' THEN ''
                    ELSE ' 사유: ' || NEW.cancellation_reason
                END,
            '/schedule',
            'reservation-canceled:' || NEW.id || ':' || sm.user_id,
            COALESCE(NEW.canceled_at, CURRENT_TIMESTAMP)
        FROM song_members sm
        JOIN songs s ON s.id = NEW.song_id
        WHERE sm.song_id = NEW.song_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_reservation_canceled_notification
AFTER UPDATE OF status ON reservations
FOR EACH ROW
EXECUTE FUNCTION create_reservation_canceled_notifications();
