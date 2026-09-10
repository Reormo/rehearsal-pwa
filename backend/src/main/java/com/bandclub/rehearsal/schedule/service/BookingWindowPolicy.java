package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.schedule.domain.BookingRound;
import com.bandclub.rehearsal.schedule.domain.BookingRoundStageWindow;
import com.bandclub.rehearsal.schedule.repository.BookingRoundStageWindowRepository;
import com.bandclub.rehearsal.song.domain.Song;
import com.bandclub.rehearsal.song.domain.StageType;
import com.bandclub.rehearsal.song.repository.StageTypeRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class BookingWindowPolicy {

    private final BookingRoundStageWindowRepository windowRepository;
    private final StageTypeRepository stageTypeRepository;

    public BookingWindowPolicy(
            BookingRoundStageWindowRepository windowRepository,
            StageTypeRepository stageTypeRepository
    ) {
        this.windowRepository = windowRepository;
        this.stageTypeRepository = stageTypeRepository;
    }

    public ResolvedWindow resolve(BookingRound round, Song song) {
        StageType stageType = stageTypeRepository
                .findByIdAndClubId(song.getStageTypeId(), song.getClubId())
                .orElseThrow(() -> new IllegalStateException(
                        "Song stage type is missing."
                ));

        BookingRoundStageWindow custom = windowRepository
                .findByBookingRoundIdAndStageTypeId(
                        round.getId(), stageType.getId()
                )
                .orElse(null);

        if (custom != null) {
            return new ResolvedWindow(
                    stageType.getId(),
                    stageType.getName(),
                    custom.getBookingOpenAt(),
                    custom.getBookingCloseAt(),
                    custom.getMaxReservationMinutes(),
                    true
            );
        }

        return new ResolvedWindow(
                stageType.getId(),
                stageType.getName(),
                round.getBookingOpenAt(),
                round.getBookingCloseAt(),
                round.getMaxReservationMinutes(),
                false
        );
    }

    public record ResolvedWindow(
            Long stageTypeId,
            String stageTypeName,
            Instant bookingOpenAt,
            Instant bookingCloseAt,
            int maxReservationMinutes,
            boolean customStageWindow
    ) {}
}
