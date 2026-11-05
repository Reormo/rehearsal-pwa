"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppShell } from "@/components/app-shell";
import { AuthUser, errorMessage } from "@/lib/api";
import { scheduleApi } from "@/lib/schedule-api";
import { RehearsalCalendar } from "@/components/rehearsal-calendar";

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
      <div>
        {isAdmin ? (
          <section className="app-card">
            <h2 className="text-lg font-bold text-slate-950">
              관리자 기능
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              초대코드, 가입 신청, 권한과 회원을 관리할 수 있습니다.
            </p>
            <Link href="/admin" className="primary-button mt-5 w-full">
              관리자 화면
            </Link>
          </section>
        ) : (
          <section className="app-card">
            <h2 className="text-lg font-bold text-slate-950">
              합주 예약
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              팀장으로 지정된 팀의 날짜와 시간을 선택해 합주를 예약할 수 있습니다.
            </p>
          </section>
        )}
      </div>

      <section className="app-card mt-4">
        <div className="flex items-center justify-between gap-4">
          <h2 className="text-lg font-bold text-slate-950">
            내 예정 합주
          </h2>
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
            {reservationsQuery.data.map((reservation) => (
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

      <RehearsalCalendar />
    </>
  );
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
