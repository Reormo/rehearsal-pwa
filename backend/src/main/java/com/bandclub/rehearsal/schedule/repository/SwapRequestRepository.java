package com.bandclub.rehearsal.schedule.repository;

import com.bandclub.rehearsal.schedule.domain.SwapRequest;
import com.bandclub.rehearsal.schedule.domain.SwapRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SwapRequestRepository extends JpaRepository<SwapRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SwapRequest s where s.id = :swapRequestId")
    Optional<SwapRequest> findByIdForUpdate(@Param("swapRequestId") Long swapRequestId);

    @Query("""
            select case when count(s) > 0 then true else false end
            from SwapRequest s
            where s.status = :status
              and (s.requesterReservationId in :reservationIds
                   or s.targetReservationId in :reservationIds)
            """)
    boolean existsParticipation(
            @Param("reservationIds") Collection<Long> reservationIds,
            @Param("status") SwapRequestStatus status
    );

    default boolean existsPendingParticipation(Collection<Long> reservationIds) {
        return existsParticipation(reservationIds, SwapRequestStatus.PENDING);
    }

    @Query("""
            select s
            from SwapRequest s
            where s.requesterReservationId in :reservationIds
               or s.targetReservationId in :reservationIds
            order by s.requestedAt desc, s.id desc
            """)
    List<SwapRequest> findAllForReservations(
            @Param("reservationIds") Collection<Long> reservationIds
    );

    List<SwapRequest> findAllByOrderByRequestedAtDescIdDesc();

    List<SwapRequest> findAllByStatusOrderByRequestedAtDescIdDesc(SwapRequestStatus status);
}
