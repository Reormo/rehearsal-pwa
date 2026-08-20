package com.bandclub.rehearsal.auth.domain;

public enum ClubRole {
    MEMBER,
    ADMIN,
    SUPER_ADMIN;

    public boolean isAdmin() {
        return this == ADMIN || this == SUPER_ADMIN;
    }
}
