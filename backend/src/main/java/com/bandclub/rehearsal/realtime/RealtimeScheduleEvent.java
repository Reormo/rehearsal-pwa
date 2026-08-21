package com.bandclub.rehearsal.realtime;

import java.time.Instant;

public record RealtimeScheduleEvent(
        String type,
        Instant occurredAt
) {
    public static final String SCHEDULE_CHANGED = "SCHEDULE_CHANGED";
}
