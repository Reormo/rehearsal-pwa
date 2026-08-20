CREATE TABLE songs (
    id BIGSERIAL PRIMARY KEY,
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    title VARCHAR(150) NOT NULL,
    status VARCHAR(20) NOT NULL,
    archived_at TIMESTAMPTZ NULL,
    created_by BIGINT NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_songs_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_songs_club_status
    ON songs (club_id, status, id);

CREATE TABLE song_members (
    id BIGSERIAL PRIMARY KEY,
    song_id BIGINT NOT NULL REFERENCES songs(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    session_name VARCHAR(50) NOT NULL,
    is_leader BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_song_members_song_user UNIQUE (song_id, user_id)
);

CREATE UNIQUE INDEX uq_one_leader_per_song
    ON song_members (song_id)
    WHERE is_leader = TRUE;

CREATE INDEX idx_song_members_user
    ON song_members (user_id, song_id);
