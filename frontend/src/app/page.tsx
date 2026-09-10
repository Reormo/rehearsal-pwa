"use client";

import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppShell, roleLabel } from "@/components/app-shell";
import { AuthUser, errorMessage } from "@/lib/api";
import {
  scheduleApi,
  UnavailableScheduleSlot,
} from "@/lib/schedule-api";

const WEEKDAYS = ["월", "화", "수", "목", "금", "토", "일"];

export default function HomePage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <HomeContent user={user} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function HomeContent({ user }: { user: AuthUser }) {
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
  const reservationsQuery = useQuery({
    queryKey: ["reservations", "mine"],
    queryFn: scheduleApi.myReservations,
  });

  return (
    <>
      <section>
        <p className="eyebrow">WELCOME</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          {user.name}님, 안녕하세요.
        </h1>
        <p className="mt-3 text-sm text-slate-500">
          예정된 합주와 전체 일정을 한눈에 확인하고 필요한 메뉴로 이동하세요.
        </p>
      </section>

      <div className="mt-7 grid gap-4 md:grid-cols-2">
        <section className="app-card">
          <p className="card-label">내 계정</p>
          <dl className="mt-4 space-y-3 text-sm">
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">아이디</dt>
              <dd className="font-semibold text-slate-900">{user.loginId}</dd>
            </div>
            <div className="flex justify-between gap-4">
              <dt className="text-slate-500">권한</dt>
              <dd className="font-semibold text-slate-900">
                {roleLabel(user.role)}
              </dd>
            </div>
          </dl>
          <Link href="/my" className="secondary-button mt-5 w-full">
            내 계정 관리
          </Link>
        </section>

        {isAdmin ? (
          <section className="app-card">
            <p className="card-label">관리자 기능</p>
            <h2 className="mt-3 text-lg font-bold text-slate-950">
              가입 신청과 회원을 관리하세요.
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              초대코드 재발급, 가입 승인·거절, 권한 변경과 회원 관리가 가능합니다.
            </p>
            <Link href="/admin" className="primary-button mt-5 w-full">
              관리자 화면
            </Link>
          </section>
        ) : (
          <section className="app-card">
            <p className="card-label">합주 예약</p>
            <h2 className="mt-3 text-lg font-bold text-slate-950">
              팀장으로 지정된 팀의 합주를 예약하세요.
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              예약 페이지에서 팀과 날짜, 시간을 선택해 합주를 잡을 수 있습니다.
            </p>
          </section>
        )}
      </div>

      <section className="app-card mt-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="card-label">내 예정 합주</p>
            <h2 className="mt-2 text-lg font-bold text-slate-950">
              앞으로 잡힌 합주
            </h2>
          </div>
          <Link
            href="/my/reservations"
            className="shrink-0 text-xs font-bold text-slate-500 underline-offset-4 hover:text-slate-950 hover:underline"
          >
            수정하기
          </Link>
        </div>

        {reservationsQuery.isPending && (
          <p className="mt-5 text-sm text-slate-400">예정 합주를 불러오는 중...</p>
        )}
        {reservationsQuery.isError && (
          <p className="error-box mt-5">{errorMessage(reservationsQuery.error)}</p>
        )}
        {reservationsQuery.data?.length === 0 && (
          <p className="mt-5 rounded-2xl bg-slate-50 px-4 py-7 text-center text-sm text-slate-500">
            예정된 합주가 없습니다.
          </p>
        )}
        {reservationsQuery.data && reservationsQuery.data.length > 0 && (
          <div className="mt-5 divide-y divide-slate-100 border-y border-slate-100">
            {reservationsQuery.data.slice(0, 4).map((reservation) => (
              <div
                key={reservation.id}
                className="flex items-center justify-between gap-4 py-4"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-slate-950">
                    {reservation.songTitle}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">
                    {formatReservationRange(
                      reservation.startAt,
                      reservation.endAt,
                    )}
                  </p>
                </div>
                <span className="count-badge shrink-0">예정</span>
              </div>
            ))}
          </div>
        )}
      </section>

      <HomeRehearsalCalendar />
    </>
  );
}

type DayReservationSummary = {
  reservationId: number;
  songTitle: string;
  startAt: string;
  endAt: string;
};

function HomeRehearsalCalendar() {
  const today = useMemo(() => new Date(), []);
  const [month, setMonth] = useState(
    () => new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDate, setSelectedDate] = useState(() => toIsoDate(today));

  const range = useMemo(() => calendarRange(month), [month]);
  const calendarQuery = useQuery({
    queryKey: ["home", "rehearsal-calendar", range.from, range.to],
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
    queryKey: ["home", "rehearsal-day", selectedDate],
    queryFn: () => scheduleApi.day(selectedDate),
    enabled: Boolean(selectedSummary?.roundId),
  });
  const reservations = useMemo(
    () => groupDayReservations(dayQuery.data?.unavailableSlots ?? []),
    [dayQuery.data?.unavailableSlots],
  );

  function moveMonth(delta: number) {
    const next = new Date(month.getFullYear(), month.getMonth() + delta, 1);
    setMonth(next);
    setSelectedDate(toIsoDate(next));
  }

  function selectDate(date: Date) {
    setSelectedDate(toIsoDate(date));
  }

  return (
    <section className="app-card mt-4 select-none">
      <div>
        <p className="card-label">전체 합주 일정</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          날짜별 합주 보기
        </h2>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          날짜를 누르면 그날 예약된 모든 팀의 합주를 시간순으로 확인할 수 있습니다.
        </p>
      </div>

      <div className="mt-5 flex items-center justify-between gap-4">
        <button
          type="button"
          className="secondary-button small-button"
          onClick={() => moveMonth(-1)}
          aria-label="이전 달"
        >
          &lt;
        </button>
        <h3 className="text-lg font-extrabold text-slate-950">
          {month.getFullYear()}년 {month.getMonth() + 1}월
        </h3>
        <button
          type="button"
          className="secondary-button small-button"
          onClick={() => moveMonth(1)}
          aria-label="다음 달"
        >
          &gt;
        </button>
      </div>

      {calendarQuery.isError && (
        <p className="error-box mt-4">{errorMessage(calendarQuery.error)}</p>
      )}

      <div className="mt-4 grid grid-cols-7 gap-1 text-center">
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
          const outside = date.getMonth() !== month.getMonth();
          const selected = selectedDate === iso;

          return (
            <HomeCalendarDay
              key={iso}
              date={date}
              outside={outside}
              selected={selected}
              onClick={() => selectDate(date)}
            />
          );
        })}
      </div>

      <div className="mt-6 border-t border-slate-200 pt-5">
        <div className="flex items-center justify-between gap-3">
          <div>
            <p className="card-label">선택한 날짜</p>
            <h3 className="mt-1 text-lg font-bold text-slate-950">
              {formatDateLabel(selectedDate)}
            </h3>
          </div>
          {selectedSummary?.roundId && (
            <span className="count-badge">
              {dayQuery.data ? `${reservations.length}건` : "불러오는 중"}
            </span>
          )}
        </div>

        {!selectedSummary?.roundId && !calendarQuery.isPending && (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
            이 날짜에는 준비된 합주 일정이 없습니다.
          </p>
        )}

        {dayQuery.isPending && dayQuery.fetchStatus !== "idle" && (
          <p className="mt-4 text-sm text-slate-400">
            합주 일정을 불러오는 중...
          </p>
        )}

        {dayQuery.isError && (
          <p className="error-box mt-4">{errorMessage(dayQuery.error)}</p>
        )}

        {dayQuery.data && reservations.length === 0 && (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            이 날짜에 예약된 합주가 없습니다.
          </p>
        )}

        {reservations.length > 0 && (
          <div className="mt-4 space-y-2">
            {reservations.map((reservation) => (
              <article
                key={reservation.reservationId}
                className="relative rounded-2xl bg-slate-50 px-4 py-4 pl-7"
              >
                <span
                  className="absolute bottom-3 left-3 top-3 w-1 rounded-full bg-slate-300"
                  aria-hidden
                />
                <p className="font-bold text-slate-950">
                  {reservation.songTitle}
                </p>
                <p className="mt-1 text-sm font-semibold text-slate-600">
                  {formatTime(reservation.startAt)} ~{" "}
                  {formatTime(reservation.endAt)}
                </p>
              </article>
            ))}
          </div>
        )}
      </div>
    </section>
  );
}

function HomeCalendarDay({
  date,
  outside,
  selected,
  onClick,
}: {
  date: Date;
  outside: boolean;
  selected: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex min-h-12 items-center justify-center rounded-xl text-sm font-bold transition ${
        selected
          ? "bg-slate-950 text-white"
          : "bg-white text-slate-800 hover:bg-slate-100"
      } ${outside && !selected ? "opacity-30" : ""}`}
    >
      {date.getDate()}
    </button>
  );
}

function groupDayReservations(
  slots: UnavailableScheduleSlot[],
): DayReservationSummary[] {
  const reservations = new Map<number, DayReservationSummary>();

  for (const slot of slots) {
    if (slot.state !== "RESERVED" || slot.reservationId == null) {
      continue;
    }

    const existing = reservations.get(slot.reservationId);
    if (!existing) {
      reservations.set(slot.reservationId, {
        reservationId: slot.reservationId,
        songTitle: slot.songTitle ?? "이름 없는 팀",
        startAt: slot.startAt,
        endAt: slot.endAt,
      });
      continue;
    }

    reservations.set(slot.reservationId, {
      ...existing,
      startAt:
        new Date(slot.startAt).getTime() < new Date(existing.startAt).getTime()
          ? slot.startAt
          : existing.startAt,
      endAt:
        new Date(slot.endAt).getTime() > new Date(existing.endAt).getTime()
          ? slot.endAt
          : existing.endAt,
    });
  }

  return [...reservations.values()].sort(
    (a, b) => new Date(a.startAt).getTime() - new Date(b.startAt).getTime(),
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
    month: "long",
    day: "numeric",
    weekday: "long",
  }).format(new Date(year, month - 1, day));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function formatReservationRange(startAt: string, endAt: string) {
  const dateFormatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
  });
  const timeFormatter = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });

  return `${dateFormatter.format(new Date(startAt))} · ${timeFormatter.format(
    new Date(startAt),
  )}~${timeFormatter.format(new Date(endAt))}`;
}
