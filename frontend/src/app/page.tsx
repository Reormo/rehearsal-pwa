"use client";

import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppShell, roleLabel } from "@/components/app-shell";

export default function HomePage() {
  return (
    <AuthGate>
      {(user) => {
        const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
        return (
          <AppShell user={user}>
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
                    <dd className="font-semibold text-slate-900">{roleLabel(user.role)}</dd>
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
          </AppShell>
        );
      }}
    </AuthGate>
  );
}
