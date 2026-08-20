package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.RoomException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RoomExceptionRepository extends JpaRepository<RoomException, Long> {

    Optional<RoomException> findByIdAndClubId(Long id, Long clubId);

    List<RoomException> findAllByClubIdAndExceptionDateOrderByBlockedStartTimeAsc(
            Long clubId,
            LocalDate exceptionDate
    );

    List<RoomException> findAllByClubIdAndExceptionDateBetweenOrderByExceptionDateAscBlockedStartTimeAsc(
            Long clubId,
            LocalDate from,
            LocalDate to
    );
}
