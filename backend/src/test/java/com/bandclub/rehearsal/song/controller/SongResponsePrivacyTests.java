package com.bandclub.rehearsal.song.controller;

import com.bandclub.rehearsal.song.domain.SongStatus;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SongResponsePrivacyTests {
    @Test
    void memberSongResponseOmitsOtherMembersLoginIdentifiersButAdminRetainsThem() {
        var member = new SongService.SongMemberView(
                7L, "private_login_id", "테스트 회원", "기타", true
        );
        var song = new SongService.SongView(
                1L, "합주곡", 2L, "축제", SongStatus.ACTIVE, null,
                Instant.EPOCH, Instant.EPOCH, List.of(member)
        );

        var memberView = SongController.SongResponse.forMember(song);
        assertNull(memberView.members().getFirst().loginId());
        assertEquals("테스트 회원", memberView.members().getFirst().name());

        var adminView = SongController.SongResponse.from(song);
        assertEquals("private_login_id", adminView.members().getFirst().loginId());
    }
}
