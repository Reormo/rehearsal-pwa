package com.bandclub.rehearsal.admin.repository;

import com.bandclub.rehearsal.admin.domain.AdminActionLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {

    List<AdminActionLog> findAllByClubIdOrderByCreatedAtDesc(Long clubId, Pageable pageable);
}
