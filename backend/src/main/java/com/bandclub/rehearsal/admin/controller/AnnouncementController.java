package com.bandclub.rehearsal.admin.controller;

import com.bandclub.rehearsal.admin.service.AnnouncementService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    public List<AnnouncementResponse> announcements(@AuthenticationPrincipal Jwt jwt) {
        return announcementService.list(userId(jwt)).stream()
                .map(AnnouncementResponse::from)
                .toList();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record AnnouncementResponse(
            Long id,
            String title,
            String content,
            boolean pinned,
            Long authorUserId,
            String authorName,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static AnnouncementResponse from(AnnouncementService.AnnouncementView view) {
            return new AnnouncementResponse(
                    view.id(),
                    view.title(),
                    view.content(),
                    view.pinned(),
                    view.authorUserId(),
                    view.authorName(),
                    view.createdAt(),
                    view.updatedAt()
            );
        }
    }
}
