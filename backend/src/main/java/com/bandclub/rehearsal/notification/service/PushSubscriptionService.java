package com.bandclub.rehearsal.notification.service;

import com.bandclub.rehearsal.auth.service.MembershipService;
import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.domain.PushSubscription;
import com.bandclub.rehearsal.notification.repository.PushSubscriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Clock;

@Service
public class PushSubscriptionService {

    private final MembershipService membershipService;
    private final PushSubscriptionRepository repository;
    private final Clock clock;

    public PushSubscriptionService(
            MembershipService membershipService,
            PushSubscriptionRepository repository,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void subscribe(
            Long userId,
            String endpoint,
            String p256dhKey,
            String authKey,
            String userAgent
    ) {
        membershipService.requireMembership(userId);

        String normalizedEndpoint = normalizeEndpoint(endpoint);
        String normalizedP256dh = requireValue(
                p256dhKey,
                "PUSH_P256DH_REQUIRED",
                "Push p256dh 키가 필요합니다."
        );
        String normalizedAuth = requireValue(
                authKey,
                "PUSH_AUTH_REQUIRED",
                "Push auth 키가 필요합니다."
        );

        PushSubscription subscription = repository
                .findByEndpoint(normalizedEndpoint)
                .orElseGet(() -> PushSubscription.create(
                        userId,
                        normalizedEndpoint,
                        normalizedP256dh,
                        normalizedAuth,
                        normalizeUserAgent(userAgent),
                        clock.instant()
                ));

        if (subscription.getId() != null) {
            subscription.refresh(
                    userId,
                    normalizedP256dh,
                    normalizedAuth,
                    normalizeUserAgent(userAgent)
            );
        }

        repository.save(subscription);
    }

    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        membershipService.requireMembership(userId);
        String normalizedEndpoint = normalizeEndpoint(endpoint);

        repository.findByEndpoint(normalizedEndpoint)
                .filter(subscription -> subscription.getUserId().equals(userId))
                .ifPresent(subscription -> subscription.disable(clock.instant()));
    }

    @Transactional(readOnly = true)
    public long activeCount(Long userId) {
        membershipService.requireMembership(userId);
        return repository
                .findAllByUserIdAndDisabledAtIsNullOrderByIdAsc(userId)
                .size();
    }

    private String normalizeEndpoint(String value) {
        String endpoint = requireValue(
                value,
                "PUSH_ENDPOINT_REQUIRED",
                "Push endpoint가 필요합니다."
        );

        try {
            URI uri = URI.create(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null) {
                throw new IllegalArgumentException("HTTPS endpoint required");
            }
        } catch (RuntimeException exception) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PUSH_ENDPOINT",
                    "올바른 HTTPS Push endpoint가 아닙니다."
            );
        }

        return endpoint;
    }

    private String requireValue(String value, String code, String message) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, code, message);
        }
        if (normalized.length() > 4096) {
            throw new AppException(
                    HttpStatus.BAD_REQUEST,
                    code + "_TOO_LONG",
                    message
            );
        }
        return normalized;
    }

    private String normalizeUserAgent(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() <= 1000
                ? value
                : value.substring(0, 1000);
    }
}
