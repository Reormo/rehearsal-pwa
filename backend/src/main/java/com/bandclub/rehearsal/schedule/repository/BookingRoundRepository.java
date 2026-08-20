package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.BookingRound;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BookingRoundRepository extends JpaRepository<BookingRound, Long> {

    Optional<BookingRound> findFirstByClubIdOrderByStartDateDesc(Long clubId);

    List<BookingRound> findAllByClubIdOrderByStartDateAsc(Long clubId);

    List<BookingRound> findAllByClubIdAndEndDateGreaterThanEqualAndStartDateLessThanEqualOrderByStartDateAsc(
            Long clubId,
            LocalDate from,
            LocalDate to
    );

    Optional<BookingRound> findByIdAndClubId(Long id, Long clubId);

    Optional<BookingRound> findByClubIdAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long clubId,
            LocalDate date1,
            LocalDate date2
    );
}
