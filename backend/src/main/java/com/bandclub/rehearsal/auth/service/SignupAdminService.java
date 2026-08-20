package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.domain.*;
import com.bandclub.rehearsal.auth.repository.ClubMemberRepository;
import com.bandclub.rehearsal.auth.repository.SignupApplicationRepository;
import com.bandclub.rehearsal.auth.repository.UserRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class SignupAdminService {

    private final MembershipService membershipService;
    private final SignupApplicationRepository signupApplicationRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final Clock clock;

    public SignupAdminService(
            MembershipService membershipService,
            SignupApplicationRepository signupApplicationRepository,
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.signupApplicationRepository = signupApplicationRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SignupApplicationView> list(Long actorUserId, SignupStatus status) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        return signupApplicationRepository.findAllByClubIdAndStatusOrderByCreatedAtAsc(actor.getClubId(), status)
                .stream()
                .map(SignupApplicationView::from)
                .toList();
    }

    @Transactional
    public MemberView approve(Long actorUserId, Long applicationId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        SignupApplication application = requirePendingApplication(applicationId, actor.getClubId());

        if (userRepository.existsByLoginIdIgnoreCaseAndDeletedAtIsNull(application.getLoginId())) {
            throw new AppException(HttpStatus.CONFLICT, "LOGIN_ID_IN_USE", "이미 사용 중인 아이디입니다.");
        }
        if (application.getPasswordHash() == null) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SIGNUP_PASSWORD_MISSING",
                    "가입 신청의 인증 정보가 이미 제거되었습니다."
            );
        }

        Instant now = clock.instant();
        User user = userRepository.save(User.active(
                application.getLoginId(),
                application.getPasswordHash(),
                application.getName(),
                now
        ));
        ClubMember membership = clubMemberRepository.save(
                ClubMember.join(actor.getClubId(), user.getId(), ClubRole.MEMBER, now)
        );
        application.approve(actorUserId, user.getId(), now);

        return MemberView.from(user, membership);
    }

    @Transactional
    public void reject(Long actorUserId, Long applicationId, String reason) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        SignupApplication application = requirePendingApplication(applicationId, actor.getClubId());
        String normalizedReason = reason == null || reason.isBlank() ? null : reason.trim();
        application.reject(actorUserId, normalizedReason, clock.instant());
    }

    private SignupApplication requirePendingApplication(Long applicationId, Long clubId) {
        SignupApplication application = signupApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "SIGNUP_APPLICATION_NOT_FOUND",
                        "가입 신청을 찾을 수 없습니다."
                ));

        if (!application.getClubId().equals(clubId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "SIGNUP_APPLICATION_NOT_FOUND", "가입 신청을 찾을 수 없습니다.");
        }
        if (application.getStatus() != SignupStatus.PENDING) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "SIGNUP_ALREADY_REVIEWED",
                    "이미 처리된 가입 신청입니다."
            );
        }
        return application;
    }

    public record SignupApplicationView(
            Long id,
            String loginId,
            String name,
            SignupStatus status,
            Long reviewedBy,
            Instant reviewedAt,
            String rejectionReason,
            Instant createdAt
    ) {
        static SignupApplicationView from(SignupApplication application) {
            return new SignupApplicationView(
                    application.getId(),
                    application.getLoginId(),
                    application.getName(),
                    application.getStatus(),
                    application.getReviewedBy(),
                    application.getReviewedAt(),
                    application.getRejectionReason(),
                    application.getCreatedAt()
            );
        }
    }

    public record MemberView(Long userId, String loginId, String name, ClubRole role) {
        static MemberView from(User user, ClubMember membership) {
            return new MemberView(user.getId(), user.getLoginId(), user.getName(), membership.getRole());
        }
    }
}
