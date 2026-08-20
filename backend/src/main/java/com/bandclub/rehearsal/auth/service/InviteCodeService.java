package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.InviteCode;
import com.bandclub.rehearsal.auth.repository.ClubRepository;
import com.bandclub.rehearsal.auth.repository.InviteCodeRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class InviteCodeService {

    private final MembershipService membershipService;
    private final ClubRepository clubRepository;
    private final InviteCodeRepository inviteCodeRepository;
    private final InviteCodeGenerator inviteCodeGenerator;
    private final Clock clock;

    public InviteCodeService(
            MembershipService membershipService,
            ClubRepository clubRepository,
            InviteCodeRepository inviteCodeRepository,
            InviteCodeGenerator inviteCodeGenerator,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.clubRepository = clubRepository;
        this.inviteCodeRepository = inviteCodeRepository;
        this.inviteCodeGenerator = inviteCodeGenerator;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public InviteCodeView current(Long actorUserId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        InviteCode code = inviteCodeRepository.findByClubIdAndRevokedAtIsNull(actor.getClubId())
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "INVITE_CODE_NOT_FOUND",
                        "현재 사용 가능한 초대코드가 없습니다."
                ));
        return InviteCodeView.from(code);
    }

    @Transactional
    public InviteCodeView rotate(Long actorUserId) {
        ClubMember actor = membershipService.requireAdmin(actorUserId);
        clubRepository.findByIdForUpdate(actor.getClubId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CLUB_NOT_FOUND", "동아리를 찾을 수 없습니다."));

        Instant now = clock.instant();
        inviteCodeRepository.findByClubIdAndRevokedAtIsNull(actor.getClubId())
                .ifPresent(code -> code.revoke(now));

        InviteCode newCode = InviteCode.issue(
                actor.getClubId(),
                uniqueCode(),
                actorUserId,
                now
        );
        inviteCodeRepository.save(newCode);
        return InviteCodeView.from(newCode);
    }

    private String uniqueCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = inviteCodeGenerator.next();
            if (!inviteCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to generate a unique invite code.");
    }

    public record InviteCodeView(String code, Instant createdAt) {
        static InviteCodeView from(InviteCode inviteCode) {
            return new InviteCodeView(inviteCode.getCode(), inviteCode.getCreatedAt());
        }
    }
}
