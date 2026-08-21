package com.bandclub.rehearsal.realtime;

import com.bandclub.rehearsal.auth.domain.ClubMember;
import com.bandclub.rehearsal.auth.service.MembershipService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
public class RealtimeSchedulePublisher {

    private final MembershipService membershipService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public RealtimeSchedulePublisher(
            MembershipService membershipService,
            SimpMessagingTemplate messagingTemplate,
            Clock clock
    ) {
        this.membershipService = membershipService;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    public void publishForUser(Long userId) {
        ClubMember membership = membershipService.requireMembership(userId);
        messagingTemplate.convertAndSend(
                destination(membership.getClubId()),
                new RealtimeScheduleEvent(
                        RealtimeScheduleEvent.SCHEDULE_CHANGED,
                        clock.instant()
                )
        );
    }

    public static String destination(Long clubId) {
        return "/topic/clubs/" + clubId + "/schedule";
    }
}
