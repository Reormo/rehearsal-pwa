CREATE TABLE room_operating_hours (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    operating_date DATE NOT NULL,
    open_minute SMALLINT NOT NULL,
    close_minute SMALLINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    updated_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_room_operating_hours_club_date
        UNIQUE (club_id, operating_date),
    CONSTRAINT ck_room_operating_hours_range
        CHECK (
            open_minute BETWEEN 0 AND 1410
            AND close_minute BETWEEN 30 AND 1440
            AND open_minute < close_minute
        ),
    CONSTRAINT ck_room_operating_hours_boundary
        CHECK (open_minute % 30 = 0 AND close_minute % 30 = 0)
);

CREATE INDEX idx_room_operating_hours_club_date
    ON room_operating_hours (club_id, operating_date);

DROP INDEX idx_room_exceptions_club_date;

ALTER TABLE room_exceptions
    DROP CONSTRAINT ck_room_exceptions_hours,
    DROP CONSTRAINT uq_room_exceptions_exact_range;

ALTER TABLE room_exceptions
    ADD COLUMN blocked_start_minute SMALLINT,
    ADD COLUMN blocked_end_minute SMALLINT;

UPDATE room_exceptions
SET blocked_start_minute =
        EXTRACT(HOUR FROM blocked_start_time)::INTEGER * 60
        + EXTRACT(MINUTE FROM blocked_start_time)::INTEGER,
    blocked_end_minute =
        EXTRACT(HOUR FROM blocked_end_time)::INTEGER * 60
        + EXTRACT(MINUTE FROM blocked_end_time)::INTEGER;

ALTER TABLE room_exceptions
    ALTER COLUMN blocked_start_minute SET NOT NULL,
    ALTER COLUMN blocked_end_minute SET NOT NULL,
    DROP COLUMN blocked_start_time,
    DROP COLUMN blocked_end_time;

ALTER TABLE room_exceptions
    ADD CONSTRAINT ck_room_exceptions_minutes
        CHECK (
            blocked_start_minute BETWEEN 0 AND 1410
            AND blocked_end_minute BETWEEN 30 AND 1440
            AND blocked_start_minute < blocked_end_minute
        ),
    ADD CONSTRAINT ck_room_exceptions_boundary
        CHECK (
            blocked_start_minute % 30 = 0
            AND blocked_end_minute % 30 = 0
        ),
    ADD CONSTRAINT uq_room_exceptions_exact_range
        UNIQUE (
            club_id,
            exception_date,
            blocked_start_minute,
            blocked_end_minute
        );

CREATE INDEX idx_room_exceptions_club_date
    ON room_exceptions (club_id, exception_date, blocked_start_minute);

-- Existing rounds created by V4-era code only contain 10:00~22:00 slots.
-- Backfill only the missing 00:00~10:00 and 22:00~24:00 30-minute atoms.
INSERT INTO reservation_slots (
    booking_round_id,
    slot_start_at,
    reservation_id,
    created_at
)
SELECT
    br.id,
    (day_value::DATE + make_interval(mins => minute_value)) AT TIME ZONE 'Asia/Seoul',
    NULL,
    CURRENT_TIMESTAMP
FROM booking_rounds br
CROSS JOIN LATERAL generate_series(
    br.start_date::TIMESTAMP,
    br.end_date::TIMESTAMP,
    INTERVAL '1 day'
) AS days(day_value)
CROSS JOIN LATERAL generate_series(0, 1410, 30) AS minutes(minute_value)
WHERE minute_value < 600 OR minute_value >= 1320
ON CONFLICT (booking_round_id, slot_start_at) DO NOTHING;
