package com.bandclub.rehearsal.schedule.service;

import com.bandclub.rehearsal.common.exception.AppException;
import com.bandclub.rehearsal.schedule.domain.RoomOperatingHours;
import com.bandclub.rehearsal.schedule.repository.RoomOperatingHoursRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

@Service
public class RoomOperatingHoursPolicy {

    public static final int DAY_MINUTES = 1440;
    public static final int SLOT_MINUTES = 30;
    public static final int DEFAULT_OPEN_MINUTE = 600;
    public static final int DEFAULT_CLOSE_MINUTE = 1320;

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    private final RoomOperatingHoursRepository repository;

    public RoomOperatingHoursPolicy(RoomOperatingHoursRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Window effective(Long clubId, LocalDate date) {
        return repository.findByClubIdAndOperatingDate(clubId, date)
                .map(this::toWindow)
                .orElseGet(() -> new Window(
                        DEFAULT_OPEN_MINUTE,
                        DEFAULT_CLOSE_MINUTE,
                        false,
                        null
                ));
    }

    public boolean contains(
            LocalDate date,
            Instant startAt,
            Instant endAt,
            Window window
    ) {
        int startMinute = minuteOffset(date, startAt);
        int endMinute = minuteOffset(date, endAt);
        return startMinute >= window.openMinute()
                && endMinute <= window.closeMinute()
                && startMinute < endMinute;
    }

    public boolean containsAtomicSlot(
            LocalDate date,
            Instant slotStartAt,
            Window window
    ) {
        int startMinute = minuteOffset(date, slotStartAt);
        int endMinute = startMinute + SLOT_MINUTES;
        return startMinute >= window.openMinute()
                && endMinute <= window.closeMinute();
    }

    public int minuteOffset(LocalDate date, Instant instant) {
        Instant dayStart = date.atStartOfDay(ScheduleService.SERVICE_ZONE).toInstant();
        long minutes = Duration.between(dayStart, instant).toMinutes();
        if (minutes < 0 || minutes > DAY_MINUTES) {
            return -1;
        }
        return Math.toIntExact(minutes);
    }

    public Instant atMinute(LocalDate date, int minute) {
        if (minute < 0 || minute > DAY_MINUTES || minute % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("minute must be a 30-minute boundary in 0..1440");
        }
        return date.atStartOfDay(ScheduleService.SERVICE_ZONE)
                .plusMinutes(minute)
                .toInstant();
    }

    public static int parseBoundary(String value) {
        if (value == null || value.isBlank()) {
            throw invalidBoundary();
        }
        String normalized = value.trim();
        if ("24:00".equals(normalized)) {
            return DAY_MINUTES;
        }
        try {
            LocalTime time = LocalTime.parse(normalized, TIME_FORMATTER);
            if (time.getSecond() != 0
                    || time.getNano() != 0
                    || (time.getMinute() != 0 && time.getMinute() != 30)) {
                throw invalidBoundary();
            }
            return time.getHour() * 60 + time.getMinute();
        } catch (DateTimeParseException exception) {
            throw invalidBoundary();
        }
    }

    public static String formatBoundary(int minute) {
        if (minute < 0 || minute > DAY_MINUTES || minute % SLOT_MINUTES != 0) {
            throw new IllegalArgumentException("minute must be a 30-minute boundary in 0..1440");
        }
        return String.format(
                Locale.ROOT,
                "%02d:%02d",
                minute / 60,
                minute % 60
        );
    }

    private Window toWindow(RoomOperatingHours operatingHours) {
        return new Window(
                operatingHours.getOpenMinute(),
                operatingHours.getCloseMinute(),
                true,
                operatingHours.getReason()
        );
    }

    private static AppException invalidBoundary() {
        return new AppException(
                HttpStatus.BAD_REQUEST,
                "INVALID_ROOM_TIME_BOUNDARY",
                "시간은 00:00~24:00 범위의 30분 경계여야 합니다."
        );
    }

    public record Window(
            int openMinute,
            int closeMinute,
            boolean overridden,
            String reason
    ) {
    }
}
