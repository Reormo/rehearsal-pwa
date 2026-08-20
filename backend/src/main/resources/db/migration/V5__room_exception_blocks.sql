-- V4 was already applied to local/development databases before the room exception model was refined.
-- Keep V4 immutable and migrate the old CLOSED/CUSTOM_HOURS model to blocked time ranges here.

CREATE TABLE room_exceptions_v2 (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    exception_date DATE NOT NULL,
    blocked_start_time TIME NOT NULL,
    blocked_end_time TIME NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_room_exceptions_v2_hours CHECK (blocked_start_time < blocked_end_time),
    CONSTRAINT uq_room_exceptions_v2_exact_range
        UNIQUE (club_id, exception_date, blocked_start_time, blocked_end_time)
);

-- A previously CLOSED day becomes one full-day blocked range.
INSERT INTO room_exceptions_v2 (
    club_id, exception_date, blocked_start_time, blocked_end_time, reason,
    created_by, created_at, updated_at
)
SELECT
    club_id, exception_date, TIME '10:00', TIME '22:00', reason,
    created_by, created_at, updated_at
FROM room_exceptions
WHERE type = 'CLOSED';

-- A previous CUSTOM_HOURS opening creates a blocked range before opening, when needed.
INSERT INTO room_exceptions_v2 (
    club_id, exception_date, blocked_start_time, blocked_end_time, reason,
    created_by, created_at, updated_at
)
SELECT
    club_id, exception_date, TIME '10:00', open_time, reason,
    created_by, created_at, updated_at
FROM room_exceptions
WHERE type = 'CUSTOM_HOURS'
  AND open_time > TIME '10:00';

-- And another blocked range after closing, when needed.
INSERT INTO room_exceptions_v2 (
    club_id, exception_date, blocked_start_time, blocked_end_time, reason,
    created_by, created_at, updated_at
)
SELECT
    club_id, exception_date, close_time, TIME '22:00', reason,
    created_by, created_at, updated_at
FROM room_exceptions
WHERE type = 'CUSTOM_HOURS'
  AND close_time < TIME '22:00';

DROP TABLE room_exceptions;

ALTER TABLE room_exceptions_v2 RENAME TO room_exceptions;
ALTER SEQUENCE room_exceptions_v2_id_seq RENAME TO room_exceptions_id_seq;
ALTER TABLE room_exceptions
    RENAME CONSTRAINT ck_room_exceptions_v2_hours TO ck_room_exceptions_hours;
ALTER TABLE room_exceptions
    RENAME CONSTRAINT uq_room_exceptions_v2_exact_range TO uq_room_exceptions_exact_range;

CREATE INDEX idx_room_exceptions_club_date
    ON room_exceptions (club_id, exception_date, blocked_start_time);
