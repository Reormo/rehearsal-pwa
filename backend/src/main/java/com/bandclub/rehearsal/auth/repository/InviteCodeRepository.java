package com.bandclub.rehearsal.auth.repository;

import com.bandclub.rehearsal.auth.domain.InviteCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteCodeRepository extends JpaRepository<InviteCode, Long> {

    Optional<InviteCode> findByCodeAndRevokedAtIsNull(String code);

    Optional<InviteCode> findByClubIdAndRevokedAtIsNull(Long clubId);

    boolean existsByCode(String code);
}
