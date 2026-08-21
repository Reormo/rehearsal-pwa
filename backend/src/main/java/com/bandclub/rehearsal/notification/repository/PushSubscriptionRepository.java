package com.bandclub.rehearsal.notification.repository;

import com.bandclub.rehearsal.notification.domain.PushSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository
        extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    List<PushSubscription> findAllByUserIdAndDisabledAtIsNullOrderByIdAsc(
            Long userId
    );
}
