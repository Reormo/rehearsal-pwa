"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, Song, songApi } from "@/lib/api";

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
  const [expandedTeamId, setExpandedTeamId] = useState<number | null>(null);
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
        {teamsQuery.data?.map((team) => (
          <TeamCard
            key={team.id}
            team={team}
            userId={userId}
            expanded={expandedTeamId === team.id}
            onToggle={() =>
              setExpandedTeamId((current) => (current === team.id ? null : team.id))
            }
          />
        ))}
      </div>
    </div>
  );
}

function TeamCard({
  team,
  userId,
  expanded,
  onToggle,
}: {
  team: Song;
  userId: number;
  expanded: boolean;
  onToggle: () => void;
}) {
  const me = team.members.find((member) => member.userId === userId);
  const leader = team.members.find((member) => member.leader);

  return (
    <article className="app-card">
      <button
        type="button"
        className="w-full text-left"
        aria-expanded={expanded}
        onClick={onToggle}
      >
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="truncate font-bold text-slate-950">{team.title}</p>
            <p className="mt-1 text-xs text-slate-500">
              팀장 {leader?.name ?? "미지정"}
              {me ? ` · 내 세션 ${me.sessionName}` : ""}
            </p>
          </div>
          <div className="flex shrink-0 flex-wrap items-center justify-end gap-2">
            <span className="count-badge">{team.stageTypeName}</span>
            <span className="count-badge">{team.members.length}명</span>
            <span className="text-xs font-bold text-slate-400">
              {expanded ? "▲" : "▼"}
            </span>
          </div>
        </div>
      </button>

      {expanded && (
        <div className="mt-4 border-t border-slate-100 pt-4">
          <p className="card-label">팀원</p>
          <div className="mt-3 space-y-2">
            {team.members.map((member) => (
              <div
                key={member.userId}
                className="flex items-center justify-between gap-3 rounded-xl bg-slate-50 px-3 py-3"
              >
                <div className="min-w-0">
                  <p className="truncate text-sm font-bold text-slate-900">
                    {member.name}
                    {member.leader && (
                      <span className="ml-2 text-xs font-semibold text-blue-600">팀장</span>
                    )}
                  </p>
                  <p className="mt-1 text-xs text-slate-500">{member.sessionName}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </article>
  );
}
