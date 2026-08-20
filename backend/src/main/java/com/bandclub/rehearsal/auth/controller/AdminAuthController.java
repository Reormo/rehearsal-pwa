package com.bandclub.rehearsal.auth.controller;

import com.bandclub.rehearsal.admin.service.AdminActionLogService;
import com.bandclub.rehearsal.auth.domain.ClubRole;
import com.bandclub.rehearsal.auth.domain.SignupStatus;
import com.bandclub.rehearsal.auth.service.InviteCodeService;
import com.bandclub.rehearsal.auth.service.MemberAdminService;
import com.bandclub.rehearsal.auth.service.SignupAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminAuthController {

    private final SignupAdminService signupAdminService;
    private final InviteCodeService inviteCodeService;
    private final MemberAdminService memberAdminService;
    private final AdminActionLogService actionLogService;

    public AdminAuthController(
            SignupAdminService signupAdminService,
            InviteCodeService inviteCodeService,
            MemberAdminService memberAdminService,
            AdminActionLogService actionLogService
    ) {
        this.signupAdminService = signupAdminService;
        this.inviteCodeService = inviteCodeService;
        this.memberAdminService = memberAdminService;
        this.actionLogService = actionLogService;
    }

    @GetMapping("/signup-applications")
    public List<SignupApplicationResponse> signupApplications(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "PENDING") SignupStatus status
    ) {
        return signupAdminService.list(userId(jwt), status).stream()
                .map(SignupApplicationResponse::from)
                .toList();
    }

    @PostMapping("/signup-applications/{applicationId}/approve")
    public MemberResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId
    ) {
        long actorUserId = userId(jwt);
        MemberResponse response = MemberResponse.from(signupAdminService.approve(actorUserId, applicationId));
        actionLogService.record(
                actorUserId,
                "SIGNUP_APPROVE",
                "SIGNUP_APPLICATION",
                applicationId,
                null,
                null,
                java.util.Map.of("approvedUserId", response.userId(), "role", response.role().name())
        );
        return response;
    }

    @PostMapping("/signup-applications/{applicationId}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody RejectSignupRequest request
    ) {
        long actorUserId = userId(jwt);
        signupAdminService.reject(actorUserId, applicationId, request.reason());
        actionLogService.record(
                actorUserId,
                "SIGNUP_REJECT",
                "SIGNUP_APPLICATION",
                applicationId,
                request.reason(),
                null,
                java.util.Map.of("rejected", true)
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invite-code")
    public InviteCodeResponse currentInviteCode(@AuthenticationPrincipal Jwt jwt) {
        return InviteCodeResponse.from(inviteCodeService.current(userId(jwt)));
    }

    @PostMapping("/invite-code/rotate")
    public InviteCodeResponse rotateInviteCode(@AuthenticationPrincipal Jwt jwt) {
        long actorUserId = userId(jwt);
        InviteCodeResponse response = InviteCodeResponse.from(inviteCodeService.rotate(actorUserId));
        actionLogService.record(
                actorUserId,
                "INVITE_CODE_ROTATE",
                "INVITE_CODE",
                null,
                null,
                null,
                java.util.Map.of("rotated", true)
        );
        return response;
    }

    @GetMapping("/members")
    public List<MemberResponse> members(@AuthenticationPrincipal Jwt jwt) {
        return memberAdminService.list(userId(jwt)).stream()
                .map(MemberResponse::from)
                .toList();
    }

    @PatchMapping("/members/{userId}/role")
    public MemberResponse changeRole(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody ChangeRoleRequest request
    ) {
        long actorUserId = userId(jwt);
        ClubRole beforeRole = memberAdminService.list(actorUserId).stream()
                .filter(member -> member.userId().equals(userId))
                .findFirst()
                .map(MemberAdminService.MemberView::role)
                .orElse(null);
        MemberResponse response = MemberResponse.from(memberAdminService.changeRole(actorUserId, userId, request.role()));
        actionLogService.record(
                actorUserId,
                "MEMBER_ROLE_CHANGE",
                "USER",
                userId,
                null,
                beforeRole == null ? null : java.util.Map.of("role", beforeRole.name()),
                java.util.Map.of("role", response.role().name())
        );
        return response;
    }


    @PatchMapping("/members/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        long actorUserId = userId(jwt);
        memberAdminService.resetPassword(actorUserId, userId, request.newPassword());
        actionLogService.record(
                actorUserId,
                "MEMBER_PASSWORD_RESET",
                "USER",
                userId,
                null,
                null,
                java.util.Map.of("passwordReset", true)
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId
    ) {
        long actorUserId = userId(jwt);
        memberAdminService.deleteMember(actorUserId, userId);
        actionLogService.record(
                actorUserId,
                "MEMBER_DELETE",
                "USER",
                userId,
                null,
                null,
                java.util.Map.of("deleted", true)
        );
        return ResponseEntity.noContent().build();
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record RejectSignupRequest(@Size(max = 500) String reason) {
    }

    public record ChangeRoleRequest(ClubRole role) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(min = 8, max = 72) String newPassword
    ) {
    }

    public record InviteCodeResponse(String code, Instant createdAt) {
        static InviteCodeResponse from(InviteCodeService.InviteCodeView view) {
            return new InviteCodeResponse(view.code(), view.createdAt());
        }
    }

    public record SignupApplicationResponse(
            Long id,
            String loginId,
            String name,
            SignupStatus status,
            Long reviewedBy,
            Instant reviewedAt,
            String rejectionReason,
            Instant createdAt
    ) {
        static SignupApplicationResponse from(SignupAdminService.SignupApplicationView view) {
            return new SignupApplicationResponse(
                    view.id(),
                    view.loginId(),
                    view.name(),
                    view.status(),
                    view.reviewedBy(),
                    view.reviewedAt(),
                    view.rejectionReason(),
                    view.createdAt()
            );
        }
    }

    public record MemberResponse(Long userId, String loginId, String name, ClubRole role) {
        static MemberResponse from(SignupAdminService.MemberView view) {
            return new MemberResponse(view.userId(), view.loginId(), view.name(), view.role());
        }

        static MemberResponse from(MemberAdminService.MemberView view) {
            return new MemberResponse(view.userId(), view.loginId(), view.name(), view.role());
        }
    }
}
