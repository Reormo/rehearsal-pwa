package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.BookingRoundStageWindow;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BookingRoundStageWindowRepository
        extends JpaRepository<BookingRoundStageWindow, Long> {

    List<BookingRoundStageWindow>
    findAllByBookingRoundIdOrderByStageTypeIdAsc(Long bookingRoundId);

    Optional<BookingRoundStageWindow>
    findByBookingRoundIdAndStageTypeId(Long bookingRoundId, Long stageTypeId);
}