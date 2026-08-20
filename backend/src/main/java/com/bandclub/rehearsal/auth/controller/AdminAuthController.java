package com.bandclub.rehearsal.auth.controller;

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

    public AdminAuthController(
            SignupAdminService signupAdminService,
            InviteCodeService inviteCodeService,
            MemberAdminService memberAdminService
    ) {
        this.signupAdminService = signupAdminService;
        this.inviteCodeService = inviteCodeService;
        this.memberAdminService = memberAdminService;
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
        return MemberResponse.from(signupAdminService.approve(userId(jwt), applicationId));
    }

    @PostMapping("/signup-applications/{applicationId}/reject")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long applicationId,
            @Valid @RequestBody RejectSignupRequest request
    ) {
        signupAdminService.reject(userId(jwt), applicationId, request.reason());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/invite-code")
    public InviteCodeResponse currentInviteCode(@AuthenticationPrincipal Jwt jwt) {
        return InviteCodeResponse.from(inviteCodeService.current(userId(jwt)));
    }

    @PostMapping("/invite-code/rotate")
    public InviteCodeResponse rotateInviteCode(@AuthenticationPrincipal Jwt jwt) {
        return InviteCodeResponse.from(inviteCodeService.rotate(userId(jwt)));
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
        return MemberResponse.from(memberAdminService.changeRole(userId(jwt), userId, request.role()));
    }


    @PatchMapping("/members/{userId}/password")
    public ResponseEntity<Void> resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId,
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        memberAdminService.resetPassword(userId(jwt), userId, request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/members/{userId}")
    public ResponseEntity<Void> deleteMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long userId
    ) {
        memberAdminService.deleteMember(userId(jwt), userId);
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
