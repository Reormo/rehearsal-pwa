"use client";

import { useQuery } from "@tanstack/react-query";
import { useMemo, useRef, useState } from "react";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import {
  BookableScheduleSlot,
  BookingRoundState,
  ScheduleDaySummary,
  scheduleApi,
  UnavailableScheduleSlot,
} from "@/lib/schedule-api";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];

export default function SchedulePage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <ScheduleContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function ScheduleContent() {
  const today = useMemo(() => new Date(), []);
  const [month, setMonth] = useState(
    () => new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDate, setSelectedDate] = useState(() => toIsoDate(today));
  const touchStartX = useRef<number | null>(null);

  const range = useMemo(() => calendarRange(month), [month]);
  const calendarQuery = useQuery({
    queryKey: ["schedule", "calendar", range.from, range.to],
    queryFn: () => scheduleApi.calendar(range.from, range.to),
  });
  const dayMap = useMemo(
    () =>
      new Map(
        (calendarQuery.data?.days ?? []).map((day) => [day.date, day] as const),
      ),
    [calendarQuery.data],
  );
  const selectedSummary = dayMap.get(selectedDate);
  const dayQuery = useQuery({
    queryKey: ["schedule", "day", selectedDate],
    queryFn: () => scheduleApi.day(selectedDate),
    enabled: Boolean(selectedSummary?.roundId),
  });

  function moveMonth(delta: number) {
    const next = new Date(month.getFullYear(), month.getMonth() + delta, 1);
    setMonth(next);
    setSelectedDate(toIsoDate(next));
  }

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">SCHEDULE</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          합주 시간표
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-500">
          DB에서는 항상 30분 단위 슬롯을 유지하고, 예약 화면에는 회차의 최대 예약
          시간에 맞춰 연속된 빈 시간을 묶어서 보여줍니다.
        </p>
      </section>

      <section
        className="app-card select-none"
        onTouchStart={(event) => {
          touchStartX.current = event.touches[0]?.clientX ?? null;
        }}
        onTouchEnd={(event) => {
          if (touchStartX.current == null) return;
          const endX = event.changedTouches[0]?.clientX ?? touchStartX.current;
          const delta = endX - touchStartX.current;
          touchStartX.current = null;
          if (Math.abs(delta) < 50) return;
          moveMonth(delta < 0 ? 1 : -1);
        }}
      >
        <div className="flex items-center justify-between gap-4">
          <button
            type="button"
            className="secondary-button small-button"
            onClick={() => moveMonth(-1)}
          >
            &lt;
          </button>
          <div className="text-center">
            <p className="card-label">월간 일정</p>
            <h2 className="mt-1 text-lg font-bold text-slate-950">
              {month.getFullYear()}년 {month.getMonth() + 1}월
            </h2>
          </div>
          <button
            type="button"
            className="secondary-button small-button"
            onClick={() => moveMonth(1)}
          >
            &gt;
          </button>
        </div>

        {calendarQuery.isError && (
          <p className="error-box mt-4">{errorMessage(calendarQuery.error)}</p>
        )}

        <div className="mt-5 grid grid-cols-7 gap-1 text-center">
          {WEEKDAYS.map((weekday) => (
            <div
              key={weekday}
              className="py-2 text-xs font-bold text-slate-400"
            >
              {weekday}
            </div>
          ))}
          {range.dates.map((date) => {
            const iso = toIsoDate(date);
            const summary = dayMap.get(iso);
            return (
              <CalendarDay
                key={iso}
                date={date}
                currentMonth={month.getMonth()}
                selected={selectedDate === iso}
                summary={summary}
                onClick={() => setSelectedDate(iso)}
              />
            );
          })}
        </div>
      </section>

      <section className="app-card">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="card-label">선택한 날짜</p>
            <h2 className="mt-2 text-xl font-bold text-slate-950">
              {formatDateLabel(selectedDate)}
            </h2>
          </div>
          {dayQuery.data && (
            <span className="count-badge">
              {roundStateLabel(dayQuery.data.round.state)}
            </span>
          )}
        </div>

        {!selectedSummary?.roundId && !calendarQuery.isPending && (
          <p className="mt-5 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
            아직 예약 회차가 준비되지 않은 날짜입니다.
          </p>
        )}
        {dayQuery.isPending && dayQuery.fetchStatus !== "idle" && (
          <p className="mt-5 text-sm text-slate-400">시간표를 불러오는 중...</p>
        )}
        {dayQuery.isError && (
          <p className="error-box mt-5">{errorMessage(dayQuery.error)}</p>
        )}
        {dayQuery.data && (
          <>
            <div className="mt-4 rounded-2xl bg-slate-50 p-4 text-sm">
              <p className="font-semibold text-slate-800">
                {dayQuery.data.round.roundNo}회차 ·{" "}
                {formatDateLabel(dayQuery.data.round.startDate)} ~{" "}
                {formatDateLabel(dayQuery.data.round.endDate)}
              </p>
              <p className="mt-1 text-slate-500">
                예약 오픈 {formatDateTime(dayQuery.data.round.bookingOpenAt)}
              </p>
              <p className="mt-1 text-slate-500">
                1회 최대 {dayQuery.data.round.maxReservationMinutes}분 · 일반 슬롯{" "}
                {dayQuery.data.standardSlots.length}개
              </p>
              {dayQuery.data.roomStatus === "PARTIAL_BLOCKED" && (
                <p className="mt-2 font-semibold text-amber-700">
                  일부 시간 사용 불가
                </p>
              )}
              {dayQuery.data.roomStatus === "CLOSED" && (
                <p className="mt-2 font-semibold text-red-600">
                  오늘은 전체 시간 사용 불가
                </p>
              )}
            </div>

            {dayQuery.data.blockedPeriods.length > 0 && (
              <div className="mt-4 rounded-2xl border border-amber-200 bg-amber-50 p-4">
                <p className="text-sm font-bold text-amber-900">사용 불가 시간</p>
                <div className="mt-2 space-y-1 text-sm text-amber-800">
                  {dayQuery.data.blockedPeriods.map((period) => (
                    <p key={period.id}>
                      {trimTime(period.blockedStartTime)} ~{" "}
                      {trimTime(period.blockedEndTime)} · {period.reason}
                    </p>
                  ))}
                </div>
              </div>
            )}

            <div className="mt-5">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <p className="card-label">일반 예약 슬롯</p>
                  <p className="mt-1 text-sm text-slate-500">
                    연속된 빈 시간을 최대 예약 시간 단위로 묶습니다.
                  </p>
                </div>
                <span className="count-badge">
                  {dayQuery.data.standardSlots.length}
                </span>
              </div>

              {dayQuery.data.standardSlots.length === 0 ? (
                <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
                  일반 예약 슬롯이 없습니다.
                </p>
              ) : (
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  {dayQuery.data.standardSlots.map((slot) => (
                    <BookableSlotRow
                      key={`${slot.startAt}-${slot.endAt}`}
                      slot={slot}
                    />
                  ))}
                </div>
              )}
            </div>

            {dayQuery.data.unavailableSlots.length > 0 && (
              <div className="mt-6">
                <p className="card-label">예약할 수 없는 시간</p>
                <div className="mt-3 grid gap-2 sm:grid-cols-2">
                  {dayQuery.data.unavailableSlots.map((slot) => (
                    <UnavailableSlotRow
                      key={`${slot.startAt}-${slot.endAt}-${slot.state}`}
                      slot={slot}
                    />
                  ))}
                </div>
              </div>
            )}

            {dayQuery.data.remainderSlots.length > 0 && (
              <div className="mt-6 border-t border-slate-200 pt-6">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="card-label">잔여 슬롯</p>
                    <p className="mt-1 text-sm leading-6 text-slate-500">
                      예약 또는 사용 불가 시간 때문에 최대 길이로 묶이지 못한 빈 구간입니다.
                      30분 단위라면 최대 시간보다 짧아도 예약할 수 있습니다.
                    </p>
                  </div>
                  <span className="count-badge">
                    {dayQuery.data.remainderSlots.length}
                  </span>
                </div>
                <div className="mt-4 grid gap-2 sm:grid-cols-2">
                  {dayQuery.data.remainderSlots.map((slot) => (
                    <BookableSlotRow
                      key={`remainder-${slot.startAt}-${slot.endAt}`}
                      slot={slot}
                      remainder
                    />
                  ))}
                </div>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}

function CalendarDay({
  date,
  currentMonth,
  selected,
  summary,
  onClick,
}: {
  date: Date;
  currentMonth: number;
  selected: boolean;
  summary?: ScheduleDaySummary;
  onClick: () => void;
}) {
  const outside = date.getMonth() !== currentMonth;
  const closed = summary?.roomStatus === "CLOSED";
  const partial = summary?.roomStatus === "PARTIAL_BLOCKED";
  const prepared = Boolean(summary?.roundId);

  return (
    <button
      type="button"
      disabled={!prepared}
      onClick={onClick}
      className={`min-h-16 rounded-xl border px-1 py-2 text-left transition ${
        selected
          ? "border-slate-950 bg-slate-950 text-white"
          : "border-slate-100 bg-white hover:border-slate-300"
      } ${outside && !selected ? "opacity-35" : ""} ${
        !prepared ? "cursor-not-allowed opacity-25" : ""
      }`}
    >
      <span className="text-xs font-bold">{date.getDate()}</span>
      <span
        className={`mt-1 block truncate text-[10px] font-semibold ${
          selected
            ? "text-white/80"
            : closed
              ? "text-red-500"
              : partial
                ? "text-amber-600"
                : "text-slate-400"
        }`}
      >
        {closed
          ? "사용 불가"
          : partial
            ? `${summary?.blockedPeriodCount ?? 0}개 예외`
            : prepared
              ? "10~22"
              : "준비 전"}
      </span>
    </button>
  );
}

function BookableSlotRow({
  slot,
  remainder = false,
}: {
  slot: BookableScheduleSlot;
  remainder?: boolean;
}) {
  return (
    <div className="flex items-center justify-between rounded-2xl border border-slate-200 px-4 py-3">
      <span className="font-semibold text-slate-900">
        {formatTime(slot.startAt)} ~ {formatTime(slot.endAt)}
      </span>
      <span className={remainder ? "text-xs font-bold text-amber-700" : "text-xs font-bold text-emerald-600"}>
        {remainder ? `잔여 ${slot.durationMinutes}분` : `${slot.durationMinutes}분 예약 가능`}
      </span>
    </div>
  );
}

function UnavailableSlotRow({ slot }: { slot: UnavailableScheduleSlot }) {
  const reserved = slot.state === "RESERVED";
  return (
    <div className="flex items-center justify-between rounded-2xl border border-slate-200 bg-slate-50 px-4 py-3">
      <span className="font-semibold text-slate-700">
        {formatTime(slot.startAt)} ~ {formatTime(slot.endAt)}
      </span>
      <span className={`text-xs font-bold ${reserved ? "text-slate-600" : "text-red-500"}`}>
        {reserved ? "예약됨" : "사용 불가"}
      </span>
    </div>
  );
}

function calendarRange(month: Date) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const mondayOffset = (first.getDay() + 6) % 7;
  const start = new Date(
    first.getFullYear(),
    first.getMonth(),
    first.getDate() - mondayOffset,
  );
  const dates = Array.from({ length: 42 }, (_, index) => {
    return new Date(
      start.getFullYear(),
      start.getMonth(),
      start.getDate() + index,
    );
  });
  return {
    from: toIsoDate(dates[0]),
    to: toIsoDate(dates[dates.length - 1]),
    dates,
  };
}

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatDateLabel(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Intl.DateTimeFormat("ko-KR", {
    month: "short",
    day: "numeric",
    weekday: "short",
  }).format(new Date(year, month - 1, day));
}

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function trimTime(value: string) {
  return value.slice(0, 5);
}

function roundStateLabel(state: BookingRoundState) {
  if (state === "UPCOMING") return "오픈 전";
  if (state === "BOOKING_OPEN") return "예약 접수 중";
  if (state === "IN_PROGRESS") return "진행 중";
  return "마감";
}
