package com.bandclub.rehearsal.realtime;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RealtimeSubscriptionInterceptor implements ChannelInterceptor {

    private static final Pattern CLUB_SCHEDULE_DESTINATION =
            Pattern.compile("^/topic/clubs/(\\d+)/schedule$");

    private final MembershipService membershipService;

    public RealtimeSubscriptionInterceptor(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(message);

        if (accessor.getCommand() != StompCommand.SUBSCRIBE) {
            return message;
        }

        String destination = accessor.getDestination();
        Matcher matcher = destination == null
                ? CLUB_SCHEDULE_DESTINATION.matcher("")
                : CLUB_SCHEDULE_DESTINATION.matcher(destination);

        if (!matcher.matches()) {
            throw new AccessDeniedException("Unsupported realtime subscription.");
        }

        long requestedClubId = Long.parseLong(matcher.group(1));
        long userId = userId(accessor.getUser());
        ClubMember membership = membershipService.requireMembership(userId);

        if (!membership.getClubId().equals(requestedClubId)) {
            throw new AccessDeniedException(
                    "You cannot subscribe to another club's realtime topic."
            );
        }

        return message;
    }

    private long userId(Principal principal) {
        if (!(principal instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AccessDeniedException(
                    "Authenticated websocket session is required."
            );
        }

        try {
            return Long.parseLong(jwtAuthentication.getToken().getSubject());
        } catch (RuntimeException exception) {
            throw new AccessDeniedException(
                    "Invalid websocket authentication subject.",
                    exception
            );
        }
    }
}
