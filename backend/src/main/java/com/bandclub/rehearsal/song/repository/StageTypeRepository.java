package com.bandclub.rehearsal.song.repository;

import com.bandclub.rehearsal.song.domain.StageType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface StageTypeRepository extends JpaRepository<StageType, Long> {
    Optional<StageType> findByClubIdAndNormalizedName(Long clubId, String normalizedName);
    Optional<StageType> findByIdAndClubId(Long id, Long clubId);
}