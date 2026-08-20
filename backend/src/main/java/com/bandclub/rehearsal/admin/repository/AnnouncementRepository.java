package com.bandclub.rehearsal.admin.repository;

import com.bandclub.rehearsal.admin.domain.Announcement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnnouncementRepository extends JpaRepository<Announcement, Long> {

    List<Announcement> findAllByClubIdAndDeletedAtIsNullOrderByPinnedDescCreatedAtDesc(Long clubId);

    Optional<Announcement> findByIdAndClubIdAndDeletedAtIsNull(Long id, Long clubId);
}
