-- MUHON_STAGE_BOOKING_V13

CREATE TABLE stage_types (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    name VARCHAR(50) NOT NULL,
    normalized_name VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_stage_types_club_normalized UNIQUE (club_id, normalized_name)
);

CREATE INDEX idx_stage_types_club_name
    ON stage_types (club_id, name, id);

INSERT INTO stage_types (
    club_id, name, normalized_name, created_at, updated_at
)
SELECT
    id, '미분류', '미분류', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM clubs
ON CONFLICT (club_id, normalized_name) DO NOTHING;

ALTER TABLE songs
    ADD COLUMN stage_type_id BIGINT;

UPDATE songs s
SET stage_type_id = st.id
FROM stage_types st
WHERE st.club_id = s.club_id
  AND st.normalized_name = '미분류';

CREATE OR REPLACE FUNCTION assign_default_song_stage_type()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.stage_type_id IS NULL THEN
        SELECT id
        INTO NEW.stage_type_id
        FROM stage_types
        WHERE club_id = NEW.club_id
          AND normalized_name = '미분류';

        IF NEW.stage_type_id IS NULL THEN
            INSERT INTO stage_types (
                club_id,
                name,
                normalized_name,
                created_at,
                updated_at
            )
            VALUES (
                NEW.club_id,
                '미분류',
                '미분류',
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT (club_id, normalized_name) DO NOTHING;

            SELECT id
            INTO NEW.stage_type_id
            FROM stage_types
            WHERE club_id = NEW.club_id
              AND normalized_name = '미분류';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_songs_default_stage_type
BEFORE INSERT ON songs
FOR EACH ROW
EXECUTE FUNCTION assign_default_song_stage_type();

ALTER TABLE songs
    ALTER COLUMN stage_type_id SET NOT NULL;

ALTER TABLE songs
    ADD CONSTRAINT fk_songs_stage_type
    FOREIGN KEY (stage_type_id)
    REFERENCES stage_types(id);

CREATE INDEX idx_songs_stage_type
    ON songs (club_id, stage_type_id, status, id);

CREATE TABLE booking_round_stage_windows (
    id BIGSERIAL PRIMARY KEY,
    booking_round_id BIGINT NOT NULL
        REFERENCES booking_rounds(id) ON DELETE CASCADE,
    stage_type_id BIGINT NOT NULL REFERENCES stage_types(id),
    booking_open_at TIMESTAMPTZ NOT NULL,
    booking_close_at TIMESTAMPTZ NOT NULL,
    updated_by BIGINT REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_booking_round_stage_window
        UNIQUE (booking_round_id, stage_type_id),
    CONSTRAINT ck_booking_round_stage_window
        CHECK (booking_open_at < booking_close_at)
);

CREATE INDEX idx_booking_round_stage_windows_round
    ON booking_round_stage_windows (booking_round_id, stage_type_id);

CREATE INDEX idx_booking_round_stage_windows_open
    ON booking_round_stage_windows (booking_open_at, booking_close_at);