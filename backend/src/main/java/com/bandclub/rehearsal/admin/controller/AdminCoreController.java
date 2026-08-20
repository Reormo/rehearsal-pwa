package com.bandclub.rehearsal.admin.controller;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.admin.service.AnnouncementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminCoreController {

    private final AnnouncementService announcementService;
    private final AdminActionLogService actionLogService;

    public AdminCoreController(
            AnnouncementService announcementService,
            AdminActionLogService actionLogService
    ) {
        this.announcementService = announcementService;
        this.actionLogService = actionLogService;
    }

    @GetMapping("/announcements")
    public List<AnnouncementController.AnnouncementResponse> announcements(@AuthenticationPrincipal Jwt jwt) {
        return announcementService.listAdmin(userId(jwt)).stream()
                .map(AnnouncementController.AnnouncementResponse::from)
                .toList();
    }

    @PostMapping("/announcements")
    public AnnouncementController.AnnouncementResponse createAnnouncement(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AnnouncementRequest request
    ) {
        return AnnouncementController.AnnouncementResponse.from(
                announcementService.create(userId(jwt), request.title(), request.content(), request.pinned())
        );
    }

    @PutMapping("/announcements/{announcementId}")
    public AnnouncementController.AnnouncementResponse updateAnnouncement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long announcementId,
            @Valid @RequestBody AnnouncementRequest request
    ) {
        return AnnouncementController.AnnouncementResponse.from(
                announcementService.update(
                        userId(jwt),
                        announcementId,
                        request.title(),
                        request.content(),
                        request.pinned()
                )
        );
    }

    @DeleteMapping("/announcements/{announcementId}")
    public ResponseEntity<Void> deleteAnnouncement(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long announcementId
    ) {
        announcementService.delete(userId(jwt), announcementId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/action-logs")
    public List<ActionLogResponse> actionLogs(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return actionLogService.list(userId(jwt), limit).stream()
                .map(ActionLogResponse::from)
                .toList();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record AnnouncementRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank String content,
            boolean pinned
    ) {
    }

    public record ActionLogResponse(
            Long id,
            Long actorUserId,
            String actorName,
            String actionType,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> beforeData,
            Map<String, Object> afterData,
            Instant createdAt
    ) {
        static ActionLogResponse from(AdminActionLogService.LogView view) {
            return new ActionLogResponse(
                    view.id(),
                    view.actorUserId(),
                    view.actorName(),
                    view.actionType(),
                    view.targetType(),
                    view.targetId(),
                    view.reason(),
                    view.beforeData(),
                    view.afterData(),
                    view.createdAt()
            );
        }
    }
}
