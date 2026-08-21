package com.bandclub.rehearsal.notification.service;

import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.notification.config.NotificationProperties;
import com.bandclub.rehearsal.notification.domain.PushSubscription;
import com.bandclub.rehearsal.notification.repository.PushSubscriptionRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.Clock;
import java.util.Map;

@Service
public class WebPushService {

    private static final Logger log =
            LoggerFactory.getLogger(WebPushService.class);
    private static final int PUSH_TTL_SECONDS = 3600;

    private final NotificationProperties properties;
    private final PushSubscriptionRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final PushService pushService;

    public WebPushService(
            NotificationProperties properties,
            PushSubscriptionRepository repository,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.pushService = createPushService(properties);
    }

    public PushConfigView config() {
        return new PushConfigView(
                pushService != null,
                pushService == null ? null : properties.vapidPublicKey()
        );
    }

    public DeliveryResult sendToUser(
            Long userId,
            String title,
            String body,
            String linkPath,
            String tag
    ) {
        if (pushService == null) {
            return new DeliveryResult(0, 0, 0);
        }

        var subscriptions =
                repository.findAllByUserIdAndDisabledAtIsNullOrderByIdAsc(userId);

        int successCount = 0;
        int disabledCount = 0;
        String payload = payload(title, body, linkPath, tag);

        for (PushSubscription subscription : subscriptions) {
            try {
                nl.martijndwars.webpush.Notification notification =
                        new nl.martijndwars.webpush.Notification(
                                subscription.getEndpoint(),
                                subscription.getP256dhKey(),
                                subscription.getAuthKey(),
                                payload.getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8
                                ),
                                PUSH_TTL_SECONDS
                        );

                var response = pushService.send(notification);
                int status = response.getStatusLine().getStatusCode();

                if (status >= 200 && status < 300) {
                    subscription.markSuccess(clock.instant());
                    repository.save(subscription);
                    successCount++;
                } else if (status == 404 || status == 410) {
                    subscription.disable(clock.instant());
                    repository.save(subscription);
                    disabledCount++;
                } else {
                    log.warn(
                            "Web Push failed with status {} for subscription {}",
                            status,
                            subscription.getId()
                    );
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                log.warn(
                        "Web Push interrupted for subscription {}",
                        subscription.getId(),
                        exception
                );
                break;
            } catch (Exception exception) {
                log.warn(
                        "Web Push failed for subscription {}",
                        subscription.getId(),
                        exception
                );
            }
        }

        return new DeliveryResult(
                subscriptions.size(),
                successCount,
                disabledCount
        );
    }

    public DeliveryResult sendTest(Long userId) {
        if (pushService == null) {
            throw new AppException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "WEB_PUSH_NOT_CONFIGURED",
                    "서버에 Web Push VAPID 키가 아직 설정되지 않았습니다."
            );
        }

        DeliveryResult result = sendToUser(
                userId,
                "푸시 알림 테스트",
                "이 알림이 보이면 현재 브라우저의 Web Push 설정이 정상입니다.",
                "/my",
                "push-test-" + clock.instant().toEpochMilli()
        );

        if (result.activeSubscriptions() == 0) {
            throw new AppException(
                    HttpStatus.CONFLICT,
                    "PUSH_SUBSCRIPTION_NOT_FOUND",
                    "활성화된 Push 구독이 없습니다."
            );
        }

        return result;
    }

    private PushService createPushService(NotificationProperties properties) {
        if (!properties.webPushEnabled()) {
            return null;
        }

        try {
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(new BouncyCastleProvider());
            }
            return new PushService(
                    properties.vapidPublicKey(),
                    properties.vapidPrivateKey(),
                    properties.vapidSubject()
            );
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Invalid Web Push VAPID key configuration.",
                    exception
            );
        }
    }

    private String payload(
            String title,
            String body,
            String linkPath,
            String tag
    ) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", title,
                    "body", body,
                    "linkPath",
                    linkPath == null ? "/notifications" : linkPath,
                    "tag",
                    tag == null ? "rehearsal-notification" : tag
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException(
                    "Failed to serialize Web Push payload.",
                    exception
            );
        }
    }

    public record PushConfigView(boolean enabled, String publicKey) {
    }

    public record DeliveryResult(
            int activeSubscriptions,
            int successCount,
            int disabledCount
    ) {
    }
}
