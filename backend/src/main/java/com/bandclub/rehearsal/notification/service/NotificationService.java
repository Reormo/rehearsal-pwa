package com.bandclub.rehearsal.notification.service;

import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.domain.Notification;
import com.bandclub.rehearsal.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class NotificationService {

    private final MembershipService membershipService;
    private final NotificationRepository notificationRepository;
    private final Clock clock;

    public NotificationService(
            MembershipService membershipService,
            NotificationRepository notificationRepository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.notificationRepository = notificationRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<NotificationView> list(Long userId) {
        membershipService.requireMembership(userId);
        return notificationRepository
                .findAllByUserIdAndDismissedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        membershipService.requireMembership(userId);
        return notificationRepository.countByUserIdAndReadAtIsNullAndDismissedAtIsNull(userId);
    }

    @Transactional
    public void markAllRead(Long userId) {
        membershipService.requireMembership(userId);
        notificationRepository.markAllRead(userId, clock.instant());
    }

    @Transactional
    public void dismiss(Long userId, Long notificationId) {
        membershipService.requireMembership(userId);
        Notification notification = notificationRepository
                .findByIdAndUserId(notificationId, userId)
                .filter(item -> item.getDismissedAt() == null)
                .orElseThrow(() -> new AppException(
                        HttpStatus.NOT_FOUND,
                        "NOTIFICATION_NOT_FOUND",
                        "알림을 찾을 수 없습니다."
                ));
        notification.dismiss(clock.instant());
    }

    private NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getType(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLinkPath(),
                notification.getReadAt(),
                notification.getCreatedAt()
        );
    }

    public record NotificationView(
            Long id,
            String type,
            String title,
            String body,
            String linkPath,
            Instant readAt,
            Instant createdAt
    ) {
    }
}
