-- MUHON_STAGE_MAX_RESERVATION_V14

ALTER TABLE booking_round_stage_windows
    ADD COLUMN max_reservation_minutes SMALLINT;

UPDATE booking_round_stage_windows w
SET max_reservation_minutes = r.max_reservation_minutes
FROM booking_rounds r
WHERE r.id = w.booking_round_id;

ALTER TABLE booking_round_stage_windows
    ALTER COLUMN max_reservation_minutes SET NOT NULL;

ALTER TABLE booking_round_stage_windows
    ADD CONSTRAINT ck_booking_round_stage_windows_max_minutes
    CHECK (max_reservation_minutes IN (30, 60, 90, 120, 150, 180));
