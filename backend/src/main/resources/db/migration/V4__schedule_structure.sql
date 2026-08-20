CREATE TABLE reservation_settings (
    club_id BIGINT PRIMARY KEY REFERENCES clubs(id),
    allow_multiple_reservations BOOLEAN NOT NULL DEFAULT FALSE,
    default_booking_open_lead_minutes INTEGER NOT NULL DEFAULT 1680,
    default_max_reservation_minutes SMALLINT NOT NULL DEFAULT 90,
    updated_by BIGINT REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reservation_settings_open_lead
        CHECK (default_booking_open_lead_minutes BETWEEN 0 AND 10080),
    CONSTRAINT ck_reservation_settings_max_minutes
        CHECK (default_max_reservation_minutes IN (30, 60, 90, 120, 150, 180))
);

CREATE TABLE booking_rounds (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    round_no INTEGER NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    booking_open_at TIMESTAMPTZ NOT NULL,
    booking_close_at TIMESTAMPTZ NOT NULL,
    max_reservation_minutes SMALLINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_booking_rounds_club_round_no UNIQUE (club_id, round_no),
    CONSTRAINT uq_booking_rounds_club_start_date UNIQUE (club_id, start_date),
    CONSTRAINT ck_booking_rounds_week CHECK (end_date = start_date + 6),
    CONSTRAINT ck_booking_rounds_max_minutes
        CHECK (max_reservation_minutes IN (30, 60, 90, 120, 150, 180)),
    CONSTRAINT ck_booking_rounds_window CHECK (booking_open_at < booking_close_at)
);

CREATE INDEX idx_booking_rounds_club_dates
    ON booking_rounds (club_id, start_date, end_date);

CREATE TABLE room_exceptions (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    exception_date DATE NOT NULL,
    type VARCHAR(20) NOT NULL,
    open_time TIME,
    close_time TIME,
    reason VARCHAR(500) NOT NULL,
    created_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_room_exceptions_club_date UNIQUE (club_id, exception_date),
    CONSTRAINT ck_room_exceptions_type CHECK (type IN ('CLOSED', 'CUSTOM_HOURS')),
    CONSTRAINT ck_room_exceptions_hours CHECK (
        (type = 'CLOSED' AND open_time IS NULL AND close_time IS NULL)
        OR
        (type = 'CUSTOM_HOURS'
            AND open_time IS NOT NULL
            AND close_time IS NOT NULL
            AND open_time < close_time)
    )
);

CREATE INDEX idx_room_exceptions_club_date
    ON room_exceptions (club_id, exception_date);

CREATE TABLE reservation_slots (
    id BIGSERIAL PRIMARY KEY,
    booking_round_id BIGINT NOT NULL REFERENCES booking_rounds(id) ON DELETE CASCADE,
    slot_start_at TIMESTAMPTZ NOT NULL,
    reservation_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_reservation_slots_round_start UNIQUE (booking_round_id, slot_start_at)
);

CREATE INDEX idx_reservation_slots_round_start
    ON reservation_slots (booking_round_id, slot_start_at);

CREATE INDEX idx_reservation_slots_reservation
    ON reservation_slots (reservation_id)
    WHERE reservation_id IS NOT NULL;

-- `reservations` is introduced in the next booking feature migration.
-- Its FK is added to reservation_slots.reservation_id at that point.
