package com.bandclub.rehearsal.song.controller;

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

    public AdminSongController(SongService songService) {
        this.songService = songService;
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
        return SongController.SongResponse.from(songService.createSong(
                userId(jwt),
                request.title(),
                request.leaderUserId(),
                request.leaderSessionName()
        ));
    }

    @PatchMapping("/{songId}")
    public SongController.SongResponse rename(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody RenameSongRequest request
    ) {
        return SongController.SongResponse.from(
                songService.renameSong(userId(jwt), songId, request.title())
        );
    }

    @PostMapping("/{songId}/archive")
    public SongController.SongResponse archive(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId
    ) {
        return SongController.SongResponse.from(songService.archiveSong(userId(jwt), songId));
    }

    @PostMapping("/{songId}/restore")
    public SongController.SongResponse restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId
    ) {
        return SongController.SongResponse.from(songService.restoreSong(userId(jwt), songId));
    }

    @PostMapping("/{songId}/members")
    public SongController.SongResponse addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody AddMemberRequest request
    ) {
        return SongController.SongResponse.from(songService.addMember(
                userId(jwt),
                songId,
                request.userId(),
                request.sessionName()
        ));
    }

    @PatchMapping("/{songId}/members/{userId}")
    public SongController.SongResponse changeSession(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeSessionRequest request
    ) {
        return SongController.SongResponse.from(songService.changeMemberSession(
                userId(jwt),
                songId,
                userId,
                request.sessionName()
        ));
    }

    @DeleteMapping("/{songId}/members/{userId}")
    public SongController.SongResponse removeMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @PathVariable Long userId
    ) {
        return SongController.SongResponse.from(
                songService.removeMember(userId(jwt), songId, userId)
        );
    }

    @PostMapping("/{songId}/leader")
    public SongController.SongResponse changeLeader(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId,
            @Valid @RequestBody ChangeLeaderRequest request
    ) {
        return SongController.SongResponse.from(
                songService.changeLeader(userId(jwt), songId, request.userId())
        );
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
