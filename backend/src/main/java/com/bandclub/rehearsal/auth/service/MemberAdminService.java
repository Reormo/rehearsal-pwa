package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.domain.*;
import com.bandclub.rehearsal.auth.repository.ClubMemberRepository;
import com.bandclub.rehearsal.auth.repository.RefreshTokenRepository;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class MemberAdminService {

    private final MembershipService membershipService;
    private final ClubMemberRepository clubMemberRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public MemberAdminService(
            MembershipService membershipService,
            ClubMemberRepository clubMemberRepository,
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.clubMemberRepository = clubMemberRepository;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MemberView> list(Long actorUserId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        List<MemberView> result = new ArrayList<>();

        for (ClubMember membership : clubMemberRepository.findAllByClubIdOrderByIdAsc(actor.getClubId())) {
            userRepository.findByIdAndStatus(membership.getUserId(), UserStatus.ACTIVE)
                    .filter(User::isActive)
                    .ifPresent(user -> result.add(MemberView.from(user, membership)));
        }
        return result;
    }

    @Transactional
    public MemberView changeRole(Long actorUserId, Long targetUserId, ClubRole requestedRole) {
        ClubMember actor = membershipService.requireSuperAdmin(actorUserId);

        if (requestedRole != ClubRole.MEMBER && requestedRole != ClubRole.ADMIN) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_ROLE_CHANGE",
                    "변경 가능한 권한은 MEMBER 또는 ADMIN입니다."
            );
        }

        ClubMember targetMembership = requireSameClubMembership(actor.getClubId(), targetUserId);
        User targetUser = requireActiveUser(targetUserId);

        if (targetMembership.getRole() == ClubRole.SUPER_ADMIN) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN_ROLE_IMMUTABLE",
                    "SUPER_ADMIN 권한은 변경할 수 없습니다."
            );
        }

        targetMembership.changeRole(requestedRole, clock.instant());
        return MemberView.from(targetUser, targetMembership);
    }


    @Transactional
    public void resetPassword(Long actorUserId, Long targetUserId, String newPassword) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        ClubMember targetMembership = requireSameClubMembership(actor.getClubId(), targetUserId);
        User targetUser = requireActiveUser(targetUserId);

        if (targetMembership.getRole() == ClubRole.SUPER_ADMIN) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN_PASSWORD_RESET_FORBIDDEN",
                    "SUPER_ADMIN 비밀번호는 관리자 초기화 대상이 아닙니다."
            );
        }

        if (actor.getRole() == ClubRole.ADMIN && targetMembership.getRole() != ClubRole.MEMBER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_CANNOT_RESET_ADMIN_PASSWORD",
                    "ADMIN은 일반 MEMBER의 비밀번호만 초기화할 수 있습니다."
            );
        }

        Instant now = clock.instant();
        targetUser.changePasswordHash(passwordEncoder.encode(newPassword), now);
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(targetUserId)
                .forEach(token -> token.revoke(now));
    }

    @Transactional
    public void deleteMember(Long actorUserId, Long targetUserId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);

        if (actorUserId.equals(targetUserId)) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "USE_SELF_DELETE",
                    "본인 탈퇴는 /api/auth/me 엔드포인트를 사용해주세요."
            );
        }

        ClubMember targetMembership = requireSameClubMembership(actor.getClubId(), targetUserId);
        User targetUser = requireActiveUser(targetUserId);

        if (targetMembership.getRole() == ClubRole.SUPER_ADMIN) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN_CANNOT_DELETE",
                    "SUPER_ADMIN은 삭제할 수 없습니다."
            );
        }

        if (actor.getRole() == ClubRole.ADMIN && targetMembership.getRole() != ClubRole.MEMBER) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "ADMIN_CANNOT_DELETE_ADMIN",
                    "ADMIN은 일반 MEMBER만 삭제할 수 있습니다."
            );
        }

        Instant now = clock.instant();
        targetUser.anonymize(now);
        refreshTokenRepository.findAllByUserIdAndRevokedAtIsNull(targetUserId)
                .forEach(token -> token.revoke(now));
    }

    private ClubMember requireSameClubMembership(Long clubId, Long userId) {
        return clubMemberRepository.findByClubIdAndUserId(clubId, userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "회원을 찾을 수 없습니다."
                ));
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .filter(User::isActive)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "MEMBER_NOT_FOUND",
                        "회원을 찾을 수 없습니다."
                ));
    }

    public record MemberView(Long userId, String loginId, String name, ClubRole role) {
        static MemberView from(User user, ClubMember membership) {
            return new MemberView(user.getId(), user.getLoginId(), user.getName(), membership.getRole());
        }
    }
}
