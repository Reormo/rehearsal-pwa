package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.RoomOperatingHours;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomOperatingHoursRepository
        extends JpaRepository<RoomOperatingHours, Long> {

    Optional<RoomOperatingHours> findByClubIdAndOperatingDate(
            Long clubId,
            LocalDate operatingDate
    );

    List<RoomOperatingHours> findAllByClubIdAndOperatingDateBetweenOrderByOperatingDateAsc(
            Long clubId,
            LocalDate from,
            LocalDate to
    );

    Optional<RoomOperatingHours> findByIdAndClubId(Long id, Long clubId);
}
