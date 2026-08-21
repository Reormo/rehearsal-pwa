package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.Reservation;
import com.bandclub.rehearsal.schedule.domain.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByBookingRoundIdAndSongIdAndStatus(
            Long bookingRoundId,
            Long songId,
            ReservationStatus status
    );

    List<Reservation> findAllBySongIdInAndStatusAndEndAtAfterOrderByStartAtAsc(
            Collection<Long> songIds,
            ReservationStatus status,
            Instant after
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Reservation r
            where r.id = :reservationId
            """)
    Optional<Reservation> findByIdForUpdate(
            @Param("reservationId") Long reservationId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Reservation r
            where r.bookingRoundId = :bookingRoundId
              and r.status = :status
              and r.startAt < :to
              and r.endAt > :from
            order by r.id asc
            """)
    List<Reservation> findOverlappingForUpdate(
            @Param("bookingRoundId") Long bookingRoundId,
            @Param("status") ReservationStatus status,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
