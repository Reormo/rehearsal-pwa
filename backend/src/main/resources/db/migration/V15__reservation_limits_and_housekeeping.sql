-- MUHON_RESERVATION_LIMITS_V15

ALTER TABLE reservation_settings
    ADD COLUMN default_max_reservations_per_song SMALLINT;

UPDATE reservation_settings
SET default_max_reservations_per_song =
    CASE WHEN allow_multiple_reservations THEN 99 ELSE 1 END;

ALTER TABLE reservation_settings
    ALTER COLUMN default_max_reservations_per_song SET DEFAULT 1,
    ALTER COLUMN default_max_reservations_per_song SET NOT NULL;

ALTER TABLE reservation_settings
    ADD CONSTRAINT ck_reservation_settings_max_reservations_per_song
    CHECK (default_max_reservations_per_song BETWEEN 1 AND 99);

ALTER TABLE booking_round_stage_windows
    ADD COLUMN max_reservations_per_song SMALLINT;

UPDATE booking_round_stage_windows w
SET max_reservations_per_song = rs.default_max_reservations_per_song
FROM booking_rounds br
JOIN reservation_settings rs ON rs.club_id = br.club_id
WHERE br.id = w.booking_round_id;

UPDATE booking_round_stage_windows
SET max_reservations_per_song = 1
WHERE max_reservations_per_song IS NULL;

ALTER TABLE booking_round_stage_windows
    ALTER COLUMN max_reservations_per_song SET DEFAULT 1,
    ALTER COLUMN max_reservations_per_song SET NOT NULL;

ALTER TABLE booking_round_stage_windows
    ADD CONSTRAINT ck_booking_round_stage_windows_max_reservations_per_song
    CHECK (max_reservations_per_song BETWEEN 1 AND 99);
