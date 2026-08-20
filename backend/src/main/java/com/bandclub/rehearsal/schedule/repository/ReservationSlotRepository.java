package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

    boolean existsByBookingRoundId(Long bookingRoundId);

    List<ReservationSlot> findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
            Long bookingRoundId,
            Instant from,
            Instant to
    );
}
