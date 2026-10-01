package com.bandclub.rehearsal.song.controller;

import com.bandclub.rehearsal.song.domain.SongStatus;
import com.bandclub.rehearsal.song.service.SongService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/songs")
public class SongController {

    private final SongService songService;

    public SongController(SongService songService) {
        this.songService = songService;
    }

    @GetMapping
    public List<SongResponse> mySongs(@AuthenticationPrincipal Jwt jwt) {
        return songService.listMySongs(userId(jwt)).stream()
                .map(SongResponse::from)
                .toList();
    }

    @GetMapping("/all")
    public List<SongCatalogResponse> allSongs(@AuthenticationPrincipal Jwt jwt) {
        return songService.listActiveSongs(userId(jwt)).stream()
                .map(SongCatalogResponse::from)
                .toList();
    }

    @GetMapping("/{songId}")
    public SongResponse mySong(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long songId
    ) {
        return SongResponse.from(songService.getMySong(userId(jwt), songId));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record SongCatalogResponse(
            Long id,
            String title,
            Long stageTypeId,
            String stageTypeName,
            int memberCount,
            String leaderName,
            String leaderSessionName,
            Instant createdAt,
            Instant updatedAt
    ) {
        static SongCatalogResponse from(SongService.SongView view) {
            SongService.SongMemberView leader = view.members().stream()
                    .filter(SongService.SongMemberView::leader)
                    .findFirst()
                    .orElse(null);
            return new SongCatalogResponse(
                    view.id(),
                    view.title(),
                    view.stageTypeId(),
                    view.stageTypeName(),
                    view.members().size(),
                    leader == null ? null : leader.name(),
                    leader == null ? null : leader.sessionName(),
                    view.createdAt(),
                    view.updatedAt()
            );
        }
    }

    public record SongResponse(
            Long id,
            String title,
            Long stageTypeId,
            String stageTypeName,
            SongStatus status,
            Instant archivedAt,
            Instant createdAt,
            Instant updatedAt,
            List<SongMemberResponse> members
    ) {
        static SongResponse from(SongService.SongView view) {
            return new SongResponse(
                    view.id(),
                    view.title(),
                    view.stageTypeId(),
                    view.stageTypeName(),
                    view.status(),
                    view.archivedAt(),
                    view.createdAt(),
                    view.updatedAt(),
                    view.members().stream().map(SongMemberResponse::from).toList()
            );
        }
    }

    public record SongMemberResponse(
            Long userId,
            String loginId,
            String name,
            String sessionName,
            boolean leader
    ) {
        static SongMemberResponse from(SongService.SongMemberView view) {
            return new SongMemberResponse(
                    view.userId(),
                    view.loginId(),
                    view.name(),
                    view.sessionName(),
                    view.leader()
            );
        }
    }
}
