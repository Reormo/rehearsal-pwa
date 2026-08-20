package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.domain.*;
import com.bandclub.rehearsal.auth.repository.*;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final SignupApplicationRepository signupApplicationRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final TokenHashService tokenHashService;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository,
            InviteCodeRepository inviteCodeRepository,
            SignupApplicationRepository signupApplicationRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            TokenHashService tokenHashService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.signupApplicationRepository = signupApplicationRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.tokenHashService = tokenHashService;
        this.clock = clock;
    }

    @Transactional
    public SignupResult submitSignup(String inviteCodeValue, String loginIdValue, String password, String nameValue) {
        String loginId = normalizeLoginId(loginIdValue);
        String name = normalizeName(nameValue);

        InviteCode inviteCode = inviteCodeRepository.findByCodeAndRevokedAtIsNull(inviteCodeValue.trim())
                .orElseThrow(() -> new AppException(
                        HttpStatus.BAD_REQUEST,
                        "INVITE_CODE_INVALID",
                        "유효하지 않은 초대코드입니다."
                ));

        if (userRepository.existsByLoginIdIgnoreCaseAndDeletedAtIsNull(loginId)) {
            throw new AppException(HttpStatus.CONFLICT, "LOGIN_ID_IN_USE", "이미 사용 중인 아이디입니다.");
        }

        if (signupApplicationRepository.existsByClubIdAndLoginIdIgnoreCaseAndStatus(
                inviteCode.getClubId(),
                loginId,
                SignupStatus.PENDING
        )) {
            throw new AppException(HttpStatus.CONFLICT, "SIGNUP_ALREADY_PENDING", "이미 승인 대기 중인 가입 신청이 있습니다.");
        }

        SignupApplication application = SignupApplication.pending(
                inviteCode.getClubId(),
                inviteCode.getId(),
                loginId,
                passwordEncoder.encode(password),
                name,
                clock.instant()
        );
        signupApplicationRepository.save(application);
        return new SignupResult(application.getId(), application.getStatus());
    }

    @Transactional
    public LoginResult login(String loginIdValue, String password) {
        String loginId = normalizeLoginId(loginIdValue);
        User user = userRepository.findByLoginIdIgnoreCaseAndDeletedAtIsNull(loginId)
                .filter(User::isActive)
                .orElseThrow(this::invalidCredentials);

        if (user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw invalidCredentials();
        }

        ClubMember membership = clubMemberRepository.findByUserId(user.getId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.FORBIDDEN,
                        "MEMBERSHIP_NOT_FOUND",
                        "동아리 회원 정보가 없습니다."
                ));

        JwtTokenService.TokenPair tokens = issueAndStore(user, membership);
        return new LoginResult(UserView.from(user, membership), tokens);
    }

    @Transactional
    public LoginResult refresh(String rawRefreshToken) {
        Jwt jwt = jwtTokenService.decodeRefresh(rawRefreshToken);

        long userId;
        try {
            userId = Long.parseLong(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw invalidRefreshToken();
        }

        String hash = tokenHashService.sha256(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHashForUpdate(hash)
                .orElseThrow(this::invalidRefreshToken);

        Instant now = clock.instant();
        if (!stored.getUserId().equals(userId) || !stored.isUsableAt(now)) {
            throw invalidRefreshToken();
        }

        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .filter(User::isActive)
                .orElseThrow(this::invalidRefreshToken);

        ClubMember membership = clubMemberRepository.findByUserId(userId)
                .orElseThrow(this::invalidRefreshToken);

        stored.rotate(now);
        JwtTokenService.TokenPair tokens = issueAndStore(user, membership);
        return new LoginResult(UserView.from(user, membership), tokens);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHashForUpdate(tokenHashService.sha256(rawRefreshToken))
                .ifPresent(token -> token.revoke(clock.instant()));
    }

    @Transactional(readOnly = true)
    public UserView me(Long userId) {
        User user = requireActiveUser(userId);
        ClubMember membership = clubMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.FORBIDDEN,
                        "MEMBERSHIP_NOT_FOUND",
                        "동아리 회원 정보가 없습니다."
                ));
        return UserView.from(user, membership);
    }

    @Transactional
    public void deleteMe(Long userId, String currentPassword) {
        User user = requireActiveUser(userId);
        ClubMember membership = clubMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.FORBIDDEN,
                        "MEMBERSHIP_NOT_FOUND",
                        "동아리 회원 정보가 없습니다."
                ));

        if (membership.getRole() == ClubRole.SUPER_ADMIN) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN_CANNOT_DELETE",
                    "SUPER_ADMIN은 탈퇴할 수 없습니다."
            );
        }

        if (user.getPasswordHash() == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AppException(
                    HttpStatus.UNAUTHORIZED,
                    "PASSWORD_MISMATCH",
                    "현재 비밀번호가 일치하지 않습니다."
            );
        }

        Instant now = clock.instant();
        user.anonymize(now);
        revokeAllTokens(userId, now);
    }

    private JwtTokenService.TokenPair issueAndStore(User user, ClubMember membership) {
        JwtTokenService.TokenPair tokens = jwtTokenService.issue(user, membership);
        Instant now = clock.instant();
        refreshTokenRepository.save(RefreshToken.issue(
                user.getId(),
                tokenHashService.sha256(tokens.refreshToken()),
                tokens.refreshExpiresAt(),
                now
        ));
        return tokens;
    }

    private void revokeAllTokens(Long userId, Instant now) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(userId);
        activeTokens.forEach(token -> token.revoke(now));
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .filter(User::isActive)
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNAUTHORIZED,
                        "USER_NOT_ACTIVE",
                        "활성 사용자 계정이 아닙니다."
                ));
    }

    private String normalizeLoginId(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "LOGIN_ID_REQUIRED", "아이디를 입력해주세요.");
        }
        return normalized;
    }

    private String normalizeName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "NAME_REQUIRED", "이름을 입력해주세요.");
        }
        return normalized;
    }

    private AppException invalidCredentials() {
        return new AppException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_CREDENTIALS",
                "아이디 또는 비밀번호가 올바르지 않습니다."
        );
    }

    private AppException invalidRefreshToken() {
        return new AppException(
                HttpStatus.UNAUTHORIZED,
                "AUTH_INVALID_REFRESH_TOKEN",
                "Refresh Token이 유효하지 않습니다."
        );
    }

    public record SignupResult(Long applicationId, SignupStatus status) {
    }

    public record LoginResult(UserView user, JwtTokenService.TokenPair tokens) {
    }

    public record UserView(Long id, String loginId, String name, Long clubId, ClubRole role) {
        static UserView from(User user, ClubMember membership) {
            return new UserView(
                    user.getId(),
                    user.getLoginId(),
                    user.getName(),
                    membership.getClubId(),
                    membership.getRole()
            );
        }
    }
}
