package com.bandclub.rehearsal.notification.repository;

import com.bandclub.rehearsal.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndReadAtIsNullAndDismissedAtIsNull(Long userId);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n
            set n.readAt = :readAt
            where n.userId = :userId
              and n.readAt is null
              and n.dismissedAt is null
            """)
    int markAllRead(
            @Param("userId") Long userId,
            @Param("readAt") Instant readAt
    );
}
