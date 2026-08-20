package com.bandclub.rehearsal.song.controller;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.song.service.SongService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/songs")
public class AdminSongController {

    private final SongService songService;
    private final AdminActionLogService actionLogService;

    public AdminSongController(SongService songService, AdminActionLogService actionLogService) {
        this.songService = songService;
        this.actionLogService = actionLogService;
    }

    @GetMapping
    public List<SongController.SongResponse> songs(@AuthenticationPrincipal Jwt jwt) {
        return songService.listAdminSongs(userId(jwt)).stream()
                .map(SongController.SongResponse::from)
                .toList();
    }

    @PostMapping
    public SongController.SongResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateSongRequest request
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(songService.createSong(
                actorUserId,
                request.title(),
                request.leaderUserId(),
                request.leaderSessionName()
        ));
        actionLogService.record(
                actorUserId,
                "SONG_CREATE",
                "SONG",
                response.id(),
                null,
                null,
                java.util.Map.of("title", response.title())
        );
        return response;
    }

    @PatchMapping("/{songId}")
    public SongController.SongResponse rename(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody RenameSongRequest request
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(
                songService.renameSong(actorUserId, songId, request.title())
        );
        actionLogService.record(
                actorUserId,
                "SONG_RENAME",
                "SONG",
                songId,
                null,
                null,
                java.util.Map.of("title", response.title())
        );
        return response;
    }

    @PostMapping("/{songId}/archive")
    public SongController.SongResponse archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(songService.archiveSong(actorUserId, songId));
        actionLogService.record(actorUserId, "SONG_ARCHIVE", "SONG", songId, null, null, java.util.Map.of("status", response.status().name()));
        return response;
    }

    @PostMapping("/{songId}/restore")
    public SongController.SongResponse restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(songService.restoreSong(actorUserId, songId));
        actionLogService.record(actorUserId, "SONG_RESTORE", "SONG", songId, null, null, java.util.Map.of("status", response.status().name()));
        return response;
    }

    @PostMapping("/{songId}/members")
    public SongController.SongResponse addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(songService.addMember(
                actorUserId,
                songId,
                request.userId(),
                request.sessionName()
        ));
        actionLogService.record(
                actorUserId,
                "SONG_MEMBER_ADD",
                "SONG",
                songId,
                null,
                null,
                java.util.Map.of("userId", request.userId(), "sessionName", request.sessionName())
        );
        return response;
    }

    @PatchMapping("/{songId}/members/{userId}")
    public SongController.SongResponse changeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeSessionRequest request
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(songService.changeMemberSession(
                actorUserId,
                songId,
                userId,
                request.sessionName()
        ));
        actionLogService.record(
                actorUserId,
                "SONG_MEMBER_SESSION_CHANGE",
                "SONG",
                songId,
                null,
                null,
                java.util.Map.of("userId", userId, "sessionName", request.sessionName())
        );
        return response;
    }

    @DeleteMapping("/{songId}/members/{userId}")
    public SongController.SongResponse removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @PathVariable Long userId
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(
                songService.removeMember(actorUserId, songId, userId)
        );
        actionLogService.record(
                actorUserId,
                "SONG_MEMBER_REMOVE",
                "SONG",
                songId,
                null,
                null,
                java.util.Map.of("userId", userId)
        );
        return response;
    }

    @PostMapping("/{songId}/leader")
    public SongController.SongResponse changeLeader(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody ChangeLeaderRequest request
    ) {
        long actorUserId = userId(jwt);
        SongController.SongResponse response = SongController.SongResponse.from(
                songService.changeLeader(actorUserId, songId, request.userId())
        );
        actionLogService.record(
                actorUserId,
                "SONG_LEADER_CHANGE",
                "SONG",
                songId,
                null,
                null,
                java.util.Map.of("leaderUserId", request.userId())
        );
        return response;
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record CreateSongRequest(
            @NotBlank @Size(max = 150) String title,
            @NotNull Long leaderUserId,
            @NotBlank @Size(max = 50) String leaderSessionName
    ) {
    }

    public record RenameSongRequest(@NotBlank @Size(max = 150) String title) {
    }

    public record AddMemberRequest(
            @NotNull Long userId,
            @NotBlank @Size(max = 50) String sessionName
    ) {
    }

    public record ChangeSessionRequest(@NotBlank @Size(max = 50) String sessionName) {
    }

    public record ChangeLeaderRequest(@NotNull Long userId) {
    }
}
