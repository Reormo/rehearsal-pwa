package com.bandclub.rehearsal.realtime;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealtimeSubscriptionInterceptorTests {

    private final MembershipService membershipService =
            mock(MembershipService.class);
    private final RealtimeSubscriptionInterceptor interceptor =
            new RealtimeSubscriptionInterceptor(membershipService);

    @Test
    void memberCanSubscribeOnlyToOwnClubScheduleTopic() {
        ClubMember membership = mock(ClubMember.class);
        when(membership.getClubId()).thenReturn(7L);
        when(membershipService.requireMembership(42L)).thenReturn(membership);

        assertDoesNotThrow(() ->
                interceptor.preSend(subscription(42L, 7L), mock(org.springframework.messaging.MessageChannel.class))
        );

        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(subscription(42L, 8L), mock(org.springframework.messaging.MessageChannel.class))
        );
    }

    private Message<byte[]> subscription(long userId, long clubId) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(
                RealtimeSchedulePublisher.destination(clubId)
        );
        accessor.setUser(authentication(userId));

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private JwtAuthenticationToken authentication(long userId) {
        Instant now = Instant.now();
        Jwt jwt = new Jwt(
                "token",
                now,
                now.plusSeconds(300),
                java.util.Map.of("alg", "HS256"),
                java.util.Map.of("sub", Long.toString(userId))
        );
        return new JwtAuthenticationToken(jwt, List.of(), "tester");
    }
}
