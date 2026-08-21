CREATE TABLE swap_requests (
    id BIGSERIAL PRIMARY KEY,
    requester_reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    target_reservation_id BIGINT NOT NULL REFERENCES reservations(id),
    requested_by BIGINT NOT NULL REFERENCES users(id),
    responded_by BIGINT REFERENCES users(id),
    status VARCHAR(20) NOT NULL,
    requester_start_snapshot TIMESTAMPTZ NOT NULL,
    requester_end_snapshot TIMESTAMPTZ NOT NULL,
    target_start_snapshot TIMESTAMPTZ NOT NULL,
    target_end_snapshot TIMESTAMPTZ NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    responded_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    CONSTRAINT ck_swap_requests_distinct_reservations
        CHECK (requester_reservation_id <> target_reservation_id),
    CONSTRAINT ck_swap_requests_status
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELED', 'EXPIRED'))
);

CREATE INDEX idx_swap_requests_requester_status
    ON swap_requests (requester_reservation_id, status, requested_at DESC);
CREATE INDEX idx_swap_requests_target_status
    ON swap_requests (target_reservation_id, status, requested_at DESC);
CREATE INDEX idx_swap_requests_status_requested
    ON swap_requests (status, requested_at DESC);

CREATE OR REPLACE FUNCTION notify_swap_request_insert()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.status = 'PENDING' THEN
        INSERT INTO notifications (
            user_id, type, title, body, link_path, dedupe_key, created_at
        )
        SELECT
            leader.user_id,
            'SWAP_REQUESTED',
            '일정 교환 요청이 왔어요',
            requester_song.title || ' 팀이 ' ||
                to_char(NEW.requester_start_snapshot AT TIME ZONE 'Asia/Seoul', 'FMMM"월" FMDD"일" HH24:MI') ||
                '~' || to_char(NEW.requester_end_snapshot AT TIME ZONE 'Asia/Seoul', 'HH24:MI') ||
                ' 예약과 ' || target_song.title || ' 팀의 ' ||
                to_char(NEW.target_start_snapshot AT TIME ZONE 'Asia/Seoul', 'FMMM"월" FMDD"일" HH24:MI') ||
                '~' || to_char(NEW.target_end_snapshot AT TIME ZONE 'Asia/Seoul', 'HH24:MI') ||
                ' 예약 교환을 요청했습니다.',
            '/my/swaps',
            'swap-requested:' || NEW.id || ':' || leader.user_id,
            NEW.requested_at
        FROM reservations requester_reservation
        JOIN songs requester_song ON requester_song.id = requester_reservation.song_id
        JOIN reservations target_reservation ON target_reservation.id = NEW.target_reservation_id
        JOIN songs target_song ON target_song.id = target_reservation.song_id
        JOIN song_members leader
          ON leader.song_id = target_reservation.song_id
         AND leader.is_leader = TRUE
        WHERE requester_reservation.id = NEW.requester_reservation_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    ELSIF NEW.status = 'ACCEPTED' THEN
        INSERT INTO notifications (
            user_id, type, title, body, link_path, dedupe_key, created_at
        )
        SELECT DISTINCT
            leader.user_id,
            'SWAP_ACCEPTED',
            '일정 교환이 완료됐어요',
            requester_song.title || ' 팀과 ' || target_song.title || ' 팀의 일정이 교환되었습니다.',
            '/my/swaps',
            'swap-accepted:' || NEW.id || ':' || leader.user_id,
            COALESCE(NEW.responded_at, NEW.requested_at)
        FROM reservations requester_reservation
        JOIN songs requester_song ON requester_song.id = requester_reservation.song_id
        JOIN reservations target_reservation ON target_reservation.id = NEW.target_reservation_id
        JOIN songs target_song ON target_song.id = target_reservation.song_id
        JOIN song_members leader
          ON leader.song_id IN (requester_reservation.song_id, target_reservation.song_id)
         AND leader.is_leader = TRUE
        WHERE requester_reservation.id = NEW.requester_reservation_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_swap_request_insert_notification
AFTER INSERT ON swap_requests
FOR EACH ROW
EXECUTE FUNCTION notify_swap_request_insert();

CREATE OR REPLACE FUNCTION notify_swap_status_change()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status <> 'PENDING' OR NEW.status = OLD.status THEN
        RETURN NEW;
    END IF;

    IF NEW.status = 'ACCEPTED' THEN
        INSERT INTO notifications (
            user_id, type, title, body, link_path, dedupe_key, created_at
        )
        SELECT DISTINCT
            leader.user_id,
            'SWAP_ACCEPTED',
            '일정 교환이 완료됐어요',
            requester_song.title || ' 팀과 ' || target_song.title || ' 팀의 일정이 교환되었습니다.',
            '/my/swaps',
            'swap-accepted:' || NEW.id || ':' || leader.user_id,
            COALESCE(NEW.responded_at, CURRENT_TIMESTAMP)
        FROM reservations requester_reservation
        JOIN songs requester_song ON requester_song.id = requester_reservation.song_id
        JOIN reservations target_reservation ON target_reservation.id = NEW.target_reservation_id
        JOIN songs target_song ON target_song.id = target_reservation.song_id
        JOIN song_members leader
          ON leader.song_id IN (requester_reservation.song_id, target_reservation.song_id)
         AND leader.is_leader = TRUE
        WHERE requester_reservation.id = NEW.requester_reservation_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    ELSIF NEW.status = 'REJECTED' THEN
        INSERT INTO notifications (
            user_id, type, title, body, link_path, dedupe_key, created_at
        )
        SELECT
            leader.user_id,
            'SWAP_REJECTED',
            '일정 교환 요청이 거절됐어요',
            target_song.title || ' 팀이 일정 교환 요청을 거절했습니다.',
            '/my/swaps',
            'swap-rejected:' || NEW.id || ':' || leader.user_id,
            COALESCE(NEW.responded_at, CURRENT_TIMESTAMP)
        FROM reservations requester_reservation
        JOIN reservations target_reservation ON target_reservation.id = NEW.target_reservation_id
        JOIN songs target_song ON target_song.id = target_reservation.song_id
        JOIN song_members leader
          ON leader.song_id = requester_reservation.song_id
         AND leader.is_leader = TRUE
        WHERE requester_reservation.id = NEW.requester_reservation_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    ELSIF NEW.status = 'CANCELED' THEN
        INSERT INTO notifications (
            user_id, type, title, body, link_path, dedupe_key, created_at
        )
        SELECT
            leader.user_id,
            'SWAP_CANCELED',
            '일정 교환 요청이 취소됐어요',
            requester_song.title || ' 팀이 일정 교환 요청을 취소했습니다.',
            '/my/swaps',
            'swap-canceled:' || NEW.id || ':' || leader.user_id,
            COALESCE(NEW.responded_at, CURRENT_TIMESTAMP)
        FROM reservations requester_reservation
        JOIN songs requester_song ON requester_song.id = requester_reservation.song_id
        JOIN reservations target_reservation ON target_reservation.id = NEW.target_reservation_id
        JOIN song_members leader
          ON leader.song_id = target_reservation.song_id
         AND leader.is_leader = TRUE
        WHERE requester_reservation.id = NEW.requester_reservation_id
        ON CONFLICT (dedupe_key) DO NOTHING;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_swap_status_notification
AFTER UPDATE OF status ON swap_requests
FOR EACH ROW
EXECUTE FUNCTION notify_swap_status_change();

CREATE OR REPLACE FUNCTION expire_pending_swaps_on_reservation_change()
RETURNS TRIGGER AS $$
DECLARE
    swap_row RECORD;
    other_reservation_id BIGINT;
BEGIN
    IF NOT (
        NEW.booking_round_id IS DISTINCT FROM OLD.booking_round_id OR
        NEW.start_at IS DISTINCT FROM OLD.start_at OR
        NEW.end_at IS DISTINCT FROM OLD.end_at OR
        NEW.status IS DISTINCT FROM OLD.status
    ) THEN
        RETURN NEW;
    END IF;

    FOR swap_row IN
        SELECT id, requester_reservation_id, target_reservation_id
        FROM swap_requests
        WHERE status = 'PENDING'
          AND (requester_reservation_id = NEW.id OR target_reservation_id = NEW.id)
        ORDER BY id
        FOR UPDATE
    LOOP
        UPDATE swap_requests
        SET status = 'EXPIRED',
            expired_at = CURRENT_TIMESTAMP
        WHERE id = swap_row.id
          AND status = 'PENDING';

        IF FOUND THEN
            other_reservation_id := CASE
                WHEN swap_row.requester_reservation_id = NEW.id
                    THEN swap_row.target_reservation_id
                ELSE swap_row.requester_reservation_id
            END;

            INSERT INTO notifications (
                user_id, type, title, body, link_path, dedupe_key, created_at
            )
            SELECT
                leader.user_id,
                'SWAP_EXPIRED',
                '일정 교환 요청이 만료됐어요',
                changed_song.title || ' 팀의 예약이 변경되어 교환 요청이 자동으로 만료되었습니다.',
                '/my/swaps',
                'swap-expired:' || swap_row.id || ':' || leader.user_id,
                CURRENT_TIMESTAMP
            FROM reservations other_reservation
            JOIN song_members leader
              ON leader.song_id = other_reservation.song_id
             AND leader.is_leader = TRUE
            JOIN songs changed_song ON changed_song.id = NEW.song_id
            WHERE other_reservation.id = other_reservation_id
            ON CONFLICT (dedupe_key) DO NOTHING;
        END IF;
    END LOOP;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_expire_pending_swaps_on_reservation_change
AFTER UPDATE OF booking_round_id, start_at, end_at, status ON reservations
FOR EACH ROW
EXECUTE FUNCTION expire_pending_swaps_on_reservation_change();
