package com.bandclub.rehearsal.auth.repository;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.domain.ClubRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {

    Optional<ClubMember> findByUserId(Long userId);

    Optional<ClubMember> findByClubIdAndUserId(Long clubId, Long userId);

    Optional<ClubMember> findByClubIdAndRole(Long clubId, ClubRole role);

    List<ClubMember> findAllByClubIdOrderByIdAsc(Long clubId);
}
