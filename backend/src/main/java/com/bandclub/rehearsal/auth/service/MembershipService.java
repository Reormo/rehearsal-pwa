package com.bandclub.rehearsal.auth.service;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.ClubRole;
import com.bandclub.rehearsal.auth.repository.ClubMemberRepository;
import com.bandclub.rehearsal.common.exception.AppException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipService {

    private final ClubMemberRepository clubMemberRepository;

    public MembershipService(ClubMemberRepository clubMemberRepository) {
        this.clubMemberRepository = clubMemberRepository;
    }

    @Transactional(readOnly = true)
    public ClubMember requireMembership(Long userId) {
        return clubMemberRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(
                        HttpStatus.FORBIDDEN,
                        "MEMBERSHIP_NOT_FOUND",
                        "동아리 회원 정보가 없습니다."
                ));
    }

    @Transactional(readOnly = true)
    public ClubMember requireAdmin(Long userId) {
        ClubMember membership = requireMembership(userId);
        if (!membership.getRole().isAdmin()) {
            throw new AppException(HttpStatus.FORBIDDEN, "ADMIN_REQUIRED", "관리자 권한이 필요합니다.");
        }
        return membership;
    }

    @Transactional(readOnly = true)
    public ClubMember requireSuperAdmin(Long userId) {
        ClubMember membership = requireMembership(userId);
        if (membership.getRole() != ClubRole.SUPER_ADMIN) {
            throw new AppException(
                    HttpStatus.FORBIDDEN,
                    "SUPER_ADMIN_REQUIRED",
                    "SUPER_ADMIN 권한이 필요합니다."
            );
        }
        return membership;
    }
}
