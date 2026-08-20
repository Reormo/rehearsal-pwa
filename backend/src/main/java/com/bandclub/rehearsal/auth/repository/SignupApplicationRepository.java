package com.bandclub.rehearsal.auth.repository;

import com.bandclub.rehearsal.auth.domain.SignupApplication;
import com.bandclub.rehearsal.auth.domain.SignupStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SignupApplicationRepository extends JpaRepository<SignupApplication, Long> {

    boolean existsByClubIdAndLoginIdIgnoreCaseAndStatus(Long clubId, String loginId, SignupStatus status);

    List<SignupApplication> findAllByClubIdAndStatusOrderByCreatedAtAsc(Long clubId, SignupStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SignupApplication s where s.id = :id")
    Optional<SignupApplication> findByIdForUpdate(@Param("id") Long id);
}
