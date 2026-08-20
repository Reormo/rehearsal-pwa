package com.bandclub.rehearsal.auth.controller;

import com.bandclub.rehearsal.auth.domain.ClubRole;
import com.bandclub.rehearsal.auth.domain.SignupStatus;
import com.bandclub.rehearsal.auth.service.AuthCookieService;
import com.bandclub.rehearsal.auth.service.AuthService;
import com.bandclub.rehearsal.common.exception.AppException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookieService cookieService;

    public AuthController(AuthService authService, AuthCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        AuthService.SignupResult result = authService.submitSignup(
                request.inviteCode(),
                request.loginId(),
                request.password(),
                request.name()
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new SignupResponse(result.applicationId(), result.status()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthUserResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request.loginId(), request.password());
        return withTokens(result);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthUserResponse> refresh(HttpServletRequest request) {
        String refreshToken = cookieService.refreshToken(request)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "AUTH_REFRESH_TOKEN_REQUIRED",
                        "Refresh Token이 없습니다."
                ));
        return withTokens(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(cookieService.refreshToken(request).orElse(null));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefresh().toString())
                .build();
    }

    @GetMapping("/me")
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return AuthUserResponse.from(authService.me(userId(jwt)));
    }

    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DeleteMeRequest request
    ) {
        authService.deleteMe(userId(jwt), request.currentPassword());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookieService.clearAccess().toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.clearRefresh().toString())
                .build();
    }

    private ResponseEntity<AuthUserResponse> withTokens(AuthService.LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieService.access(result.tokens().accessToken()).toString())
                .header(HttpHeaders.SET_COOKIE, cookieService.refresh(result.tokens().refreshToken()).toString())
                .body(AuthUserResponse.from(result.user()));
    }

    private long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }

    public record SignupRequest(
            @NotBlank @Size(max = 100) String inviteCode,
            @NotBlank @Size(min = 4, max = 50) String loginId,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 50) String name
    ) {
    }

    public record SignupResponse(Long applicationId, SignupStatus status) {
    }

    public record LoginRequest(
            @NotBlank @Size(min = 4, max = 50) String loginId,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {
    }

    public record DeleteMeRequest(
            @NotBlank @Size(min = 8, max = 72) String currentPassword
    ) {
    }

    public record AuthUserResponse(
            Long id,
            String loginId,
            String name,
            Long clubId,
            ClubRole role
    ) {
        static AuthUserResponse from(AuthService.UserView view) {
            return new AuthUserResponse(view.id(), view.loginId(), view.name(), view.clubId(), view.role());
        }
    }
}
