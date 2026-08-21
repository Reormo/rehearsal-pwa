"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, songApi } from "@/lib/api";

export default function MyTeamsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <TeamsContent userId={user.id} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function TeamsContent({ userId }: { userId: number }) {
  const teamsQuery = useQuery({
    queryKey: ["songs", "mine"],
    queryFn: songApi.mine,
  });

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">ALL · TEAMS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            내 팀
          </h1>
        </div>
        <Link href="/songs" className="secondary-button">
          전체 팀 보기
        </Link>
      </section>

      {teamsQuery.isPending && (
        <p className="app-card text-sm text-slate-400">팀 정보를 불러오는 중...</p>
      )}
      {teamsQuery.isError && (
        <p className="error-box">{errorMessage(teamsQuery.error)}</p>
      )}
      {teamsQuery.data?.length === 0 && (
        <p className="app-card text-center text-sm text-slate-500">
          현재 참여 중인 팀이 없습니다.
        </p>
      )}

      <div className="space-y-3">
        {teamsQuery.data?.map((team) => {
          const me = team.members.find((member) => member.userId === userId);
          const leader = team.members.find((member) => member.leader);

          return (
            <article key={team.id} className="app-card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <p className="truncate font-bold text-slate-950">{team.title}</p>
                  <p className="mt-1 text-xs text-slate-500">
                    팀장 {leader?.name ?? "미지정"}
                    {me ? ` · 내 세션 ${me.sessionName}` : ""}
                  </p>
                </div>
                <span className="count-badge shrink-0">{team.members.length}명</span>
              </div>
            </article>
          );
        })}
      </div>
    </div>
  );
}
