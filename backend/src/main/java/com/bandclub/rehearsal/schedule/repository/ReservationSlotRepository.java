package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.ReservationSlot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface ReservationSlotRepository extends JpaRepository<ReservationSlot, Long> {

    boolean existsByBookingRoundId(Long bookingRoundId);

    List<ReservationSlot> findAllByBookingRoundIdAndSlotStartAtGreaterThanEqualAndSlotStartAtLessThanOrderBySlotStartAtAsc(
            Long bookingRoundId,
            Instant from,
            Instant to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ReservationSlot s
            where s.bookingRoundId = :bookingRoundId
              and s.slotStartAt >= :from
              and s.slotStartAt < :to
            order by s.slotStartAt asc
            """)
    List<ReservationSlot> findRangeForUpdate(
            @Param("bookingRoundId") Long bookingRoundId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ReservationSlot s
            where (s.bookingRoundId = :firstRoundId
                   and s.slotStartAt >= :firstFrom
                   and s.slotStartAt < :firstTo)
               or (s.bookingRoundId = :secondRoundId
                   and s.slotStartAt >= :secondFrom
                   and s.slotStartAt < :secondTo)
            order by s.slotStartAt asc, s.id asc
            """)
    List<ReservationSlot> findSwapWindowsForUpdate(
            @Param("firstRoundId") Long firstRoundId,
            @Param("firstFrom") Instant firstFrom,
            @Param("firstTo") Instant firstTo,
            @Param("secondRoundId") Long secondRoundId,
            @Param("secondFrom") Instant secondFrom,
            @Param("secondTo") Instant secondTo
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select s
            from ReservationSlot s
            where s.reservationId in :reservationIds
            order by s.slotStartAt asc
            """)
    List<ReservationSlot> findAllByReservationIdInForUpdate(
            @Param("reservationIds") Collection<Long> reservationIds
    );
}
