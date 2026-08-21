CREATE OR REPLACE FUNCTION create_reservation_changed_notifications()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'ACTIVE'
       AND NEW.status = 'ACTIVE'
       AND (
           OLD.start_at IS DISTINCT FROM NEW.start_at
           OR OLD.end_at IS DISTINCT FROM NEW.end_at
       ) THEN
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
            'RESERVATION_CHANGED',
            '합주 예약 시간이 변경됐어요',
            s.title || ' · ' ||
                to_char(OLD.start_at AT TIME ZONE 'Asia/Seoul', 'FMMM"월" FMDD"일" HH24:MI') ||
                '~' || to_char(OLD.end_at AT TIME ZONE 'Asia/Seoul', 'HH24:MI') ||
                ' → ' ||
                to_char(NEW.start_at AT TIME ZONE 'Asia/Seoul', 'FMMM"월" FMDD"일" HH24:MI') ||
                '~' || to_char(NEW.end_at AT TIME ZONE 'Asia/Seoul', 'HH24:MI') ||
                '로 변경되었습니다.',
            '/my/reservations',
            NULL,
            COALESCE(NEW.updated_at, CURRENT_TIMESTAMP)
        FROM song_members sm
        JOIN songs s ON s.id = NEW.song_id
        WHERE sm.song_id = NEW.song_id;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_reservation_changed_notification ON reservations;

CREATE TRIGGER trg_reservation_changed_notification
AFTER UPDATE OF start_at, end_at ON reservations
FOR EACH ROW
EXECUTE FUNCTION create_reservation_changed_notifications();
