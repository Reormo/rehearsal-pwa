package com.bandclub.rehearsal.notification.controller;

import com.bandclub.rehearsal.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public List<NotificationResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(userId(jwt)).stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new UnreadCountResponse(notificationService.unreadCount(userId(jwt)));
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllRead(userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> dismiss(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long notificationId
    ) {
        notificationService.dismiss(userId(jwt), notificationId);
        return ResponseEntity.noContent().build();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record UnreadCountResponse(long count) {
    }

    public record NotificationResponse(
            Long id,
            String type,
            String title,
            String body,
            String linkPath,
            Instant readAt,
            Instant createdAt
    ) {
        static NotificationResponse from(NotificationService.NotificationView view) {
            return new NotificationResponse(
                    view.id(),
                    view.type(),
                    view.title(),
                    view.body(),
                    view.linkPath(),
                    view.readAt(),
                    view.createdAt()
            );
        }
    }
}
