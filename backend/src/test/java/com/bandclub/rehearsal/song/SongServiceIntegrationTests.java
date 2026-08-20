package com.bandclub.rehearsal.song;

import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.AuthService;
import com.bandclub.rehearsal.auth.service.InviteCodeService;
import com.bandclub.rehearsal.auth.service.SignupAdminService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.song.domain.SongStatus;
import com.bandclub.rehearsal.song.service.SongService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class SongServiceIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:18.4");

    @Autowired
    SongService songService;

    @Autowired
    AuthService authService;

    @Autowired
    InviteCodeService inviteCodeService;

    @Autowired
    SignupAdminService signupAdminService;

    @Autowired
    UserRepository userRepository;

    @Test
    @Transactional
    void adminCanManageSongMembersLeaderAndArchiveLifecycle() {
        long superAdminId = superAdminId();
        long guitaristId = createApprovedMember(superAdminId, "guitar01", "기타 회원");
        long vocalistId = createApprovedMember(superAdminId, "vocal01", "보컬 회원");

        SongService.SongView created = songService.createSong(
                superAdminId,
                "아지랑이",
                guitaristId,
                "기타"
        );
        assertEquals(SongStatus.ACTIVE, created.status());
        assertEquals(1, created.members().size());
        assertTrue(created.members().getFirst().leader());

        SongService.SongView withVocal = songService.addMember(
                superAdminId,
                created.id(),
                vocalistId,
                "보컬"
        );
        assertEquals(2, withVocal.members().size());

        SongService.SongView leaderChanged = songService.changeLeader(
                superAdminId,
                created.id(),
                vocalistId
        );
        assertTrue(leaderChanged.members().stream()
                .anyMatch(member -> member.userId().equals(vocalistId) && member.leader()));
        assertFalse(leaderChanged.members().stream()
                .anyMatch(member -> member.userId().equals(guitaristId) && member.leader()));

        assertThrows(
                AppException.class,
                () -> songService.removeMember(superAdminId, created.id(), vocalistId)
        );

        SongService.SongView archived = songService.archiveSong(superAdminId, created.id());
        assertEquals(SongStatus.ARCHIVED, archived.status());
        assertTrue(songService.listMySongs(guitaristId).isEmpty());

        SongService.SongView restored = songService.restoreSong(superAdminId, created.id());
        assertEquals(SongStatus.ACTIVE, restored.status());
        assertEquals(1, songService.listMySongs(guitaristId).size());
    }

    @Test
    @Transactional
    void memberCanParticipateInMultipleSongsAndOnlySeesOwnActiveSongs() {
        long superAdminId = superAdminId();
        long memberId = createApprovedMember(superAdminId, "multi01", "멀티 회원");
        long otherId = createApprovedMember(superAdminId, "other01", "다른 회원");

        songService.createSong(superAdminId, "첫 번째 곡", memberId, "기타");
        songService.createSong(superAdminId, "두 번째 곡", memberId, "보컬");
        songService.createSong(superAdminId, "다른 팀 곡", otherId, "드럼");

        var mine = songService.listMySongs(memberId);
        assertEquals(2, mine.size());
        assertTrue(mine.stream().allMatch(song ->
                song.members().stream().anyMatch(member -> member.userId().equals(memberId))
        ));
    }

    private long superAdminId() {
        return userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull("superadmin")
                .orElseThrow()
                .getId();
    }

    private long createApprovedMember(long superAdminId, String loginId, String name) {
        String inviteCode = inviteCodeService.current(superAdminId).code();
        AuthService.SignupResult signup = authService.submitSignup(
                inviteCode,
                loginId,
                "MemberPassword123!",
                name
        );
        return signupAdminService.approve(superAdminId, signup.applicationId()).userId();
    }
}
