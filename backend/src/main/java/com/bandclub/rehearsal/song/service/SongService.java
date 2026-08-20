package com.bandclub.rehearsal.song.service;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.auth.domain.UserStatus;
import com.bandclub.rehearsal.auth.repository.ClubMemberRepository;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.song.domain.Song;
import com.bandclub.rehearsal.song.domain.SongMember;
import com.bandclub.rehearsal.song.domain.SongStatus;
import com.bandclub.rehearsal.song.repository.SongMemberRepository;
import com.bandclub.rehearsal.song.repository.SongRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class SongService {

    private final MembershipService membershipService;
    private final ClubMemberRepository clubMemberRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final SongMemberRepository songMemberRepository;
    private final Clock clock;

    public SongService(
            MembershipService membershipService,
            ClubMemberRepository clubMemberRepository,
            UserRepository userRepository,
            SongRepository songRepository,
            SongMemberRepository songMemberRepository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.clubMemberRepository = clubMemberRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.songMemberRepository = songMemberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SongView> listMySongs(Long userId) {
        ClubMember membership = membershipService.requireMembership(userId);
        LinkedHashSet<Long> songIds = new LinkedHashSet<>();
        songMemberRepository.findAllByUserIdOrderByIdAsc(userId)
                .forEach(member -> songIds.add(member.getSongId()));

        List<SongView> result = new ArrayList<>();
        for (Long songId : songIds) {
            songRepository.findByIdAndClubId(songId, membership.getClubId())
                    .filter(Song::isActive)
                    .ifPresent(song -> result.add(toView(song)));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public SongView getMySong(Long userId, Long songId) {
        ClubMember membership = membershipService.requireMembership(userId);
        Song song = songRepository.findByIdAndClubId(songId, membership.getClubId())
                .filter(Song::isActive)
                .orElseThrow(() -> notFound());

        if (!songMemberRepository.existsBySongIdAndUserId(songId, userId)) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SONG_PARTICIPATION_REQUIRED",
                    "참여 중인 곡만 조회할 수 있습니다."
            );
        }
        return toView(song);
    }

    @Transactional(readOnly = true)
    public List<SongView> listAdminSongs(Long actorUserId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        return songRepository.findAllByClubIdOrderByIdAsc(actor.getClubId()).stream()
                .map(this::toView)
                .toList();
    }

    @Transactional
    public SongView createSong(
            Long actorUserId,
            String title,
            Long leaderUserId,
            String leaderSessionName
    ) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        requireActiveSameClubUser(actor.getClubId(), leaderUserId);

        Instant now = clock.instant();
        Song song = songRepository.save(Song.active(
                actor.getClubId(),
                normalizeTitle(title),
                actorUserId,
                now
        ));
        songMemberRepository.save(SongMember.join(
                song.getId(),
                leaderUserId,
                normalizeSession(leaderSessionName),
                true,
                now
        ));
        return toView(song);
    }

    @Transactional
    public SongView renameSong(Long actorUserId, Long songId, String title) {
        Song song = requireSongForUpdate(actorUserId, songId);
        song.rename(normalizeTitle(title), clock.instant());
        return toView(song);
    }

    @Transactional
    public SongView archiveSong(Long actorUserId, Long songId) {
        Song song = requireSongForUpdate(actorUserId, songId);
        song.archive(clock.instant());
        return toView(song);
    }

    @Transactional
    public SongView restoreSong(Long actorUserId, Long songId) {
        Song song = requireSongForUpdate(actorUserId, songId);
        if (songMemberRepository.findBySongIdAndLeaderTrue(songId).isEmpty()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_LEADER_REQUIRED",
                    "팀장이 없는 곡은 복구할 수 없습니다."
            );
        }
        song.restore(clock.instant());
        return toView(song);
    }

    @Transactional
    public SongView addMember(
            Long actorUserId,
            Long songId,
            Long userId,
            String sessionName
    ) {
        Song song = requireSongForUpdate(actorUserId, songId);
        requireActive(song);
        requireActiveSameClubUser(song.getClubId(), userId);

        if (songMemberRepository.existsBySongIdAndUserId(songId, userId)) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_MEMBER_ALREADY_EXISTS",
                    "이미 이 곡에 참여 중인 회원입니다."
            );
        }

        songMemberRepository.save(SongMember.join(
                songId,
                userId,
                normalizeSession(sessionName),
                false,
                clock.instant()
        ));
        return toView(song);
    }

    @Transactional
    public SongView changeMemberSession(
            Long actorUserId,
            Long songId,
            Long userId,
            String sessionName
    ) {
        Song song = requireSongForUpdate(actorUserId, songId);
        requireActive(song);
        SongMember member = requireSongMember(songId, userId);
        member.changeSession(normalizeSession(sessionName), clock.instant());
        return toView(song);
    }

    @Transactional
    public SongView changeLeader(Long actorUserId, Long songId, Long userId) {
        Song song = requireSongForUpdate(actorUserId, songId);
        requireActive(song);
        SongMember target = requireSongMember(songId, userId);
        SongMember current = songMemberRepository.findBySongIdAndLeaderTrue(songId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.CONFLICT,
                        "SONG_LEADER_REQUIRED",
                        "현재 팀장 정보가 없습니다."
                ));

        if (current.getUserId().equals(userId)) {
            return toView(song);
        }

        Instant now = clock.instant();
        current.releaseLeader(now);
        songMemberRepository.flush();
        target.appointLeader(now);
        songMemberRepository.flush();
        return toView(song);
    }

    @Transactional
    public SongView removeMember(Long actorUserId, Long songId, Long userId) {
        Song song = requireSongForUpdate(actorUserId, songId);
        requireActive(song);
        SongMember member = requireSongMember(songId, userId);

        if (member.isLeader()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SONG_LEADER_CANNOT_BE_REMOVED",
                    "팀장을 먼저 다른 참여자로 변경한 뒤 삭제해주세요."
            );
        }

        songMemberRepository.delete(member);
        songMemberRepository.flush();
        return toView(song);
    }

    private Song requireSongForUpdate(Long actorUserId, Long songId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        return songRepository.findForUpdate(songId, actor.getClubId())
                .orElseThrow(this::notFound);
    }

    private SongMember requireSongMember(Long songId, Long userId) {
        return songMemberRepository.findBySongIdAndUserId(songId, userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SONG_MEMBER_NOT_FOUND",
                        "곡 참여자를 찾을 수 없습니다."
                ));
    }

    private User requireActiveSameClubUser(Long clubId, Long userId) {
        clubMemberRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "동아리 회원을 찾을 수 없습니다."
                ));
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .filter(User::isActive)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "활성 회원을 찾을 수 없습니다."
                ));
    }

    private void requireActive(Song song) {
        if (!song.isActive()) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "ARCHIVED_SONG_READ_ONLY",
                    "보관된 곡은 복구한 뒤 수정할 수 있습니다."
            );
        }
    }

    private String normalizeTitle(String title) {
        String value = title == null ? "" : title.trim();
        if (value.isBlank() || value.length() > 150) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SONG_TITLE",
                    "곡 제목은 1자 이상 150자 이하로 입력해주세요."
            );
        }
        return value;
    }

    private String normalizeSession(String sessionName) {
        String value = sessionName == null ? "" : sessionName.trim();
        if (value.isBlank() || value.length() > 50) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_SESSION_NAME",
                    "세션은 1자 이상 50자 이하로 입력해주세요."
            );
        }
        return value;
    }

    private SongView toView(Song song) {
        List<SongMemberView> members = songMemberRepository
                .findAllBySongIdOrderByLeaderDescIdAsc(song.getId())
                .stream()
                .map(this::toMemberView)
                .toList();

        return new SongView(
                song.getId(),
                song.getTitle(),
                song.getStatus(),
                song.getArchivedAt(),
                song.getCreatedAt(),
                song.getUpdatedAt(),
                members
        );
    }

    private SongMemberView toMemberView(SongMember member) {
        User user = userRepository.findById(member.getUserId())
                .orElseThrow(() -> new IllegalStateException("Song member user is missing."));
        return new SongMemberView(
                user.getId(),
                user.getLoginId(),
                user.getName(),
                member.getSessionName(),
                member.isLeader()
        );
    }

    private AppException notFound() {
        return new AppException(
                HttpStatus.NOT_FOUND,
                "SONG_NOT_FOUND",
                "곡을 찾을 수 없습니다."
        );
    }

    public record SongView(
            Long id,
            String title,
            SongStatus status,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            List<SongMemberView> members
    ) {
    }

    public record SongMemberView(
            Long userId,
            String loginId,
            String name,
            String sessionName,
            boolean leader
    ) {
    }
}
