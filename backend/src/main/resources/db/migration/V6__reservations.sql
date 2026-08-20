CREATE TABLE reservations (
    id BIGSERIAL PRIMARY KEY,
    booking_round_id BIGINT NOT NULL REFERENCES booking_rounds(id),
    song_id BIGINT NOT NULL REFERENCES songs(id),
    start_at TIMESTAMPTZ NOT NULL,
    end_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(20) NOT NULL,
    source VARCHAR(20) NOT NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    canceled_by BIGINT REFERENCES users(id),
    cancellation_reason VARCHAR(500),
    canceled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_reservations_time CHECK (start_at < end_at),
    CONSTRAINT ck_reservations_status CHECK (status IN ('ACTIVE', 'CANCELED')),
    CONSTRAINT ck_reservations_source CHECK (source IN ('TEAM', 'ADMIN')),
    CONSTRAINT ck_reservations_active_cancel_fields CHECK (
        status <> 'ACTIVE'
        OR (canceled_by IS NULL AND cancellation_reason IS NULL AND canceled_at IS NULL)
    ),
    CONSTRAINT ck_reservations_canceled_at CHECK (
        status <> 'CANCELED'
        OR canceled_at IS NOT NULL
    )
);

CREATE INDEX idx_reservations_song_status
    ON reservations (song_id, status);

CREATE INDEX idx_reservations_round_start
    ON reservations (booking_round_id, start_at);

CREATE INDEX idx_reservations_time
    ON reservations (start_at, end_at);

ALTER TABLE reservation_slots
    ADD CONSTRAINT fk_reservation_slots_reservation
    FOREIGN KEY (reservation_id) REFERENCES reservations(id);
