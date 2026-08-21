"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppShell, roleLabel } from "@/components/app-shell";
import { AuthUser, errorMessage } from "@/lib/api";
import { scheduleApi } from "@/lib/schedule-api";

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
          인증과 권한 연동이 완료됐어요. 다음 기능부터 실제 합주 일정이 이곳에 들어옵니다.
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
              다음 단계에서 곡과 팀을 연결합니다.
            </h2>
            <p className="mt-2 text-sm leading-6 text-slate-500">
              현재는 로그인·회원가입·권한 기능까지 실제 서버와 연결된 상태입니다.
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
