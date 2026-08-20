"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, songApi } from "@/lib/api";

export default function SongsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <SongsContent isAdmin={user.role === "ADMIN" || user.role === "SUPER_ADMIN"} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function SongsContent({ isAdmin }: { isAdmin: boolean }) {
  const songsQuery = useQuery({
    queryKey: ["songs", "mine"],
    queryFn: songApi.mine,
  });

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">MY SONGS</p>
          <h1 className="mt-2 text-2xl font-black tracking-tight text-slate-950">내 곡 / 팀</h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            참여 중인 활성 곡과 팀장, 세션 구성을 확인할 수 있어요.
          </p>
        </div>
        {isAdmin && (
          <Link href="/admin/songs" className="secondary-button">
            곡 / 팀 관리
          </Link>
        )}
      </section>

      {songsQuery.isPending && (
        <div className="app-card text-sm text-slate-500">곡 정보를 불러오고 있어요.</div>
      )}

      {songsQuery.isError && (
        <div className="error-box">{errorMessage(songsQuery.error)}</div>
      )}

      {songsQuery.data?.length === 0 && (
        <div className="app-card text-center">
          <p className="text-sm font-semibold text-slate-700">현재 참여 중인 활성 곡이 없어요.</p>
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {songsQuery.data?.map((song) => {
          const leader = song.members.find((member) => member.leader);
          return (
            <article key={song.id} className="app-card">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="card-label">ACTIVE SONG</p>
                  <h2 className="mt-2 text-xl font-black text-slate-950">{song.title}</h2>
                  <p className="mt-1 text-sm text-slate-500">
                    팀장 {leader ? `${leader.name} · ${leader.sessionName}` : "미지정"}
                  </p>
                </div>
                <span className="count-badge">{song.members.length}명</span>
              </div>

              <div className="mt-5 divide-y divide-slate-100 border-t border-slate-100">
                {song.members.map((member) => (
                  <div key={member.userId} className="flex items-center justify-between gap-3 py-3">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-slate-900">
                        {member.name}
                        {member.leader && (
                          <span className="ml-2 text-xs font-extrabold text-slate-500">팀장</span>
                        )}
                      </p>
                      {member.loginId && (
                        <p className="mt-0.5 truncate text-xs text-slate-400">@{member.loginId}</p>
                      )}
                    </div>
                    <span className="rounded-full bg-slate-100 px-3 py-1 text-xs font-bold text-slate-600">
                      {member.sessionName}
                    </span>
                  </div>
                ))}
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
