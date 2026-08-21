package com.bandclub.rehearsal.notification.controller;

import com.bandclub.rehearsal.notification.service.NotificationPreferenceService;
import com.bandclub.rehearsal.notification.service.PushSubscriptionService;
import com.bandclub.rehearsal.notification.service.WebPushService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/notifications")
public class NotificationSettingsController {

    private final NotificationPreferenceService preferenceService;
    private final PushSubscriptionService pushSubscriptionService;
    private final WebPushService webPushService;

    public NotificationSettingsController(
            NotificationPreferenceService preferenceService,
            PushSubscriptionService pushSubscriptionService,
            WebPushService webPushService
    ) {
        this.preferenceService = preferenceService;
        this.pushSubscriptionService = pushSubscriptionService;
        this.webPushService = webPushService;
    }

    @GetMapping("/settings")
    public SettingsResponse settings(@AuthenticationPrincipal Jwt jwt) {
        return SettingsResponse.from(
                preferenceService.get(userId(jwt))
        );
    }

    @PutMapping("/settings")
    public SettingsResponse updateSettings(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpdateSettingsRequest request
    ) {
        return SettingsResponse.from(
                preferenceService.update(
                        userId(jwt),
                        request.rehearsalReminderMinutes()
                )
        );
    }

    @GetMapping("/push/config")
    public PushConfigResponse pushConfig(@AuthenticationPrincipal Jwt jwt) {
        preferenceService.get(userId(jwt));
        var config = webPushService.config();
        return new PushConfigResponse(
                config.enabled(),
                config.publicKey()
        );
    }

    @GetMapping("/push/status")
    public PushStatusResponse pushStatus(@AuthenticationPrincipal Jwt jwt) {
        return new PushStatusResponse(
                pushSubscriptionService.activeCount(userId(jwt))
        );
    }

    @PutMapping("/push/subscription")
    public ResponseEntity<Void> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PushSubscriptionRequest request,
            HttpServletRequest httpRequest
    ) {
        pushSubscriptionService.subscribe(
                userId(jwt),
                request.endpoint(),
                request.p256dh(),
                request.auth(),
                httpRequest.getHeader("User-Agent")
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push/unsubscribe")
    public ResponseEntity<Void> unsubscribe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PushUnsubscribeRequest request
    ) {
        pushSubscriptionService.unsubscribe(
                userId(jwt),
                request.endpoint()
        );
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/push/test")
    public PushTestResponse testPush(@AuthenticationPrincipal Jwt jwt) {
        var result = webPushService.sendTest(userId(jwt));
        return new PushTestResponse(
                result.activeSubscriptions(),
                result.successCount(),
                result.disabledCount()
        );
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record UpdateSettingsRequest(
            Integer rehearsalReminderMinutes
    ) {
    }

    public record SettingsResponse(
            Integer rehearsalReminderMinutes,
            Instant updatedAt
    ) {
        static SettingsResponse from(
                NotificationPreferenceService.SettingsView view
        ) {
            return new SettingsResponse(
                    view.rehearsalReminderMinutes(),
                    view.updatedAt()
            );
        }
    }

    public record PushConfigResponse(boolean enabled, String publicKey) {
    }

    public record PushStatusResponse(long activeSubscriptions) {
    }

    public record PushSubscriptionRequest(
            @NotBlank @Size(max = 4096) String endpoint,
            @NotBlank @Size(max = 4096) String p256dh,
            @NotBlank @Size(max = 4096) String auth
    ) {
    }

    public record PushUnsubscribeRequest(
            @NotBlank @Size(max = 4096) String endpoint
    ) {
    }

    public record PushTestResponse(
            int activeSubscriptions,
            int successCount,
            int disabledCount
    ) {
    }
}
