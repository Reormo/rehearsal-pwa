package com.bandclub.rehearsal.auth.repository;

import com.bandclub.rehearsal.auth.domain.User;
import com.bandclub.rehearsal.auth.domain.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByLoginIdIgnoreCaseAndDeletedAtIsNull(String loginId);

    boolean existsByLoginIdIgnoreCaseAndDeletedAtIsNull(String loginId);

    Optional<User> findByIdAndStatus(Long id, UserStatus status);
}
