-- Separate MT manager grants from rehearsal ADMIN roles. SUPER_ADMIN is an implicit MT manager.
CREATE TABLE mt_host_managers (
    club_id BIGINT NOT NULL REFERENCES clubs(id),
    user_id BIGINT NOT NULL REFERENCES users(id),
    granted_by BIGINT NOT NULL REFERENCES users(id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (club_id,user_id),
    CONSTRAINT fk_mt_host_club_member FOREIGN KEY (club_id,user_id) REFERENCES club_members(club_id,user_id)
);
