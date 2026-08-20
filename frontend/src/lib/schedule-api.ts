import { request } from "@/lib/api";

export type BookingRoundState =
  | "UPCOMING"
  | "BOOKING_OPEN"
  | "IN_PROGRESS"
  | "CLOSED";

export type SlotState = "OPEN" | "CLOSED" | "RESERVED";
export type RoomStatus = "OPEN" | "PARTIAL_BLOCKED" | "CLOSED";
export type ReservationStatus = "ACTIVE" | "CANCELED";
export type ReservationSource = "TEAM" | "ADMIN";

export type ScheduleSettings = {
  allowMultipleReservations: boolean;
  defaultBookingOpenLeadMinutes: number;
  defaultMaxReservationMinutes: number;
  updatedBy: number | null;
  updatedAt: string;
};

export type BookingRound = {
  id: number;
  roundNo: number;
  startDate: string;
  endDate: string;
  bookingOpenAt: string;
  bookingCloseAt: string;
  maxReservationMinutes: number;
  state: BookingRoundState;
};

export type ScheduleDaySummary = {
  date: string;
  roundId: number | null;
  roundNo: number | null;
  roundState: BookingRoundState | null;
  roomStatus: RoomStatus;
  blockedPeriodCount: number;
};

export type ScheduleCalendar = {
  from: string;
  to: string;
  days: ScheduleDaySummary[];
};

export type BookableScheduleSlot = {
  startAt: string;
  endAt: string;
  durationMinutes: number;
};

export type UnavailableScheduleSlot = {
  startAt: string;
  endAt: string;
  state: Exclude<SlotState, "OPEN">;
  reservationId: number | null;
  songId: number | null;
  songTitle: string | null;
};

export type DaySchedule = {
  date: string;
  round: BookingRound;
  roomStatus: RoomStatus;
  blockedPeriods: RoomException[];
  standardSlots: BookableScheduleSlot[];
  remainderSlots: BookableScheduleSlot[];
  unavailableSlots: UnavailableScheduleSlot[];
};

export type RoomException = {
  id: number;
  date: string;
  blockedStartTime: string;
  blockedEndTime: string;
  reason: string;
  createdBy: number | null;
  createdAt: string;
  updatedAt: string;
};

export type BookingTimeOption = {
  startAt: string;
  endAt: string;
};

export type BookingOptions = {
  date: string;
  durationMinutes: number;
  maxReservationMinutes: number;
  acceptingReservations: boolean;
  options: BookingTimeOption[];
};

export type Reservation = {
  id: number;
  bookingRoundId: number;
  songId: number;
  songTitle: string;
  startAt: string;
  endAt: string;
  status: ReservationStatus;
  source: ReservationSource;
  createdBy: number;
  canceledBy: number | null;
  cancellationReason: string | null;
  canceledAt: string | null;
  createdAt: string;
  updatedAt: string;
};

export const scheduleApi = {
  calendar(from: string, to: string) {
    return request<ScheduleCalendar>(
      `/api/schedule/calendar?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    );
  },

  day(date: string) {
    return request<DaySchedule>(`/api/schedule/days/${encodeURIComponent(date)}`);
  },

  bookingOptions(date: string, durationMinutes: number) {
    return request<BookingOptions>(
      `/api/reservations/options?date=${encodeURIComponent(date)}&durationMinutes=${encodeURIComponent(durationMinutes)}`,
    );
  },

  createReservation(input: {
    songId: number;
    startAt: string;
    durationMinutes: number;
  }) {
    return request<Reservation>("/api/reservations", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  myReservations() {
    return request<Reservation[]>("/api/reservations/mine");
  },
};

export const scheduleAdminApi = {
  settings() {
    return request<ScheduleSettings>("/api/admin/schedule/settings");
  },

  updateSettings(input: {
    allowMultipleReservations: boolean;
    defaultBookingOpenLeadMinutes: number;
    defaultMaxReservationMinutes: number;
  }) {
    return request<ScheduleSettings>("/api/admin/schedule/settings", {
      method: "PATCH",
      body: JSON.stringify(input),
    });
  },

  rounds() {
    return request<BookingRound[]>("/api/admin/schedule/rounds");
  },

  updateRound(
    roundId: number,
    input: { bookingOpenAt: string; maxReservationMinutes: number },
  ) {
    return request<BookingRound>(`/api/admin/schedule/rounds/${roundId}`, {
      method: "PATCH",
      body: JSON.stringify(input),
    });
  },

  exceptions(from: string, to: string) {
    return request<RoomException[]>(
      `/api/admin/schedule/exceptions?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    );
  },

  createException(input: {
    date: string;
    blockedStartTime: string;
    blockedEndTime: string;
    reason: string;
  }) {
    return request<RoomException>("/api/admin/schedule/exceptions", {
      method: "POST",
      body: JSON.stringify(input),
    });
  },

  deleteException(exceptionId: number) {
    return request<void>(`/api/admin/schedule/exceptions/${exceptionId}`, {
      method: "DELETE",
    });
  },
};
