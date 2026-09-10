package com.bandclub.rehearsal.notification.service;

import com.bandclub.rehearsal.schedule.service.SwapService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;

@Service
public class NotificationFanoutService {

    private final JdbcTemplate jdbcTemplate;
    private final WebPushService webPushService;
    private final Clock clock;

    public NotificationFanoutService(
            JdbcTemplate jdbcTemplate,
            WebPushService webPushService,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.webPushService = webPushService;
        this.clock = clock;
    }

    public void announcementCreated(
            Long announcementId,
            String announcementTitle
    ) {
        List<Long> recipients = jdbcTemplate.queryForList(
                """
                select cm.user_id
                from announcements a
                join club_members cm on cm.club_id = a.club_id
                join users u on u.id = cm.user_id
                where a.id = ?
                  and a.deleted_at is null
                  and u.status = 'ACTIVE'
                order by cm.user_id
                """,
                Long.class,
                announcementId
        );

        String title = "새 공지가 등록됐어요";
        String body = announcementTitle;
        String linkPath = "/announcements";

        for (Long userId : recipients) {
            String dedupeKey =
                    "announcement:" + announcementId + ":" + userId;
            if (insertNotification(
                    userId,
                    "ANNOUNCEMENT",
                    title,
                    body,
                    linkPath,
                    dedupeKey
            )) {
                webPushService.sendToUser(
                        userId,
                        title,
                        body,
                        linkPath,
                        "announcement-" + announcementId
                );
            }
        }
    }

    public void swapRequested(SwapService.SwapView swap) {
        if (swap == null || swap.target() == null || swap.requester() == null) {
            return;
        }

        List<Long> recipients = jdbcTemplate.queryForList(
                """
                select sm.user_id
                from song_members sm
                join users u on u.id = sm.user_id
                where sm.song_id = ?
                  and sm.is_leader = true
                  and u.status = 'ACTIVE'
                order by sm.user_id
                """,
                Long.class,
                swap.target().songId()
        );

        String title = "일정 교환 요청이 왔어요";
        String body = swap.requester().songTitle()
                + " 팀이 "
                + swap.target().songTitle()
                + " 팀에 일정 교환을 요청했습니다.";

        for (Long userId : recipients) {
            webPushService.sendToUser(
                    userId,
                    title,
                    body,
                    "/my/swaps",
                    "swap-requested-" + swap.id()
            );
        }
    }

    private boolean insertNotification(
            Long userId,
            String type,
            String title,
            String body,
            String linkPath,
            String dedupeKey
    ) {
        List<Long> inserted = jdbcTemplate.query(
                """
                insert into notifications (
                    user_id,
                    type,
                    title,
                    body,
                    link_path,
                    dedupe_key,
                    created_at
                )
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (dedupe_key) do nothing
                returning id
                """,
                (rs, rowNum) -> rs.getLong("id"),
                userId,
                type,
                title,
                body,
                linkPath,
                dedupeKey,
                Timestamp.from(clock.instant())
        );
        return !inserted.isEmpty();
    }
}
