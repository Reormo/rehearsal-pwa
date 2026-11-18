-- Preserve the distinction between a successful buzzer claim and an explicit MC close.
ALTER TABLE mt_events ADD COLUMN buzzer_paused BOOLEAN NOT NULL DEFAULT FALSE;
