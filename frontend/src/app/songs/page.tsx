"use client";

import Link from "next/link";
import { useMemo, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, songApi } from "@/lib/api";

type SongSortKey = "LATEST" | "NAME" | "STAGE";
type SortDirection = "ASC" | "DESC";

export default function SongsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <SongsContent
            isAdmin={user.role === "ADMIN" || user.role === "SUPER_ADMIN"}
          />
        </AppShell>
      )}
    </AuthGate>
  );
}

function SongsContent({ isAdmin }: { isAdmin: boolean }) {
  const [stageTypeFilter, setStageTypeFilter] = useState("ALL");
  const [sortKey, setSortKey] = useState<SongSortKey>("NAME");
  const [sortDirection, setSortDirection] = useState<SortDirection>("ASC");

  const songsQuery = useQuery({
    queryKey: ["songs", "all"],
    queryFn: songApi.all,
  });

  const songs = useMemo(() => songsQuery.data ?? [], [songsQuery.data]);
  const stageTypes = useMemo(
    () =>
      [...new Set(songs.map((song) => song.stageTypeName))]
        .filter(Boolean)
        .sort((a, b) => compareSongText(a, b)),
    [songs],
  );

  const visibleSongs = useMemo(() => {
    const filtered = songs.filter(
      (song) =>
        stageTypeFilter === "ALL" || song.stageTypeName === stageTypeFilter,
    );

    return [...filtered].sort((first, second) => {
      const direction = sortDirection === "ASC" ? 1 : -1;
      if (sortKey === "LATEST") {
        return (
          (new Date(first.createdAt).getTime() -
            new Date(second.createdAt).getTime()) *
          direction
        );
      }
      if (sortKey === "STAGE") {
        const stageCompare = compareSongText(
          first.stageTypeName,
          second.stageTypeName,
        );
        return (
          (stageCompare || compareSongText(first.title, second.title)) *
          direction
        );
      }
      return compareSongText(first.title, second.title) * direction;
    });
  }, [songs, sortDirection, sortKey, stageTypeFilter]);

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">ALL SONGS</p>
          <h1 className="mt-2 text-2xl font-black tracking-tight text-slate-950">
            전체 곡 보기
          </h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            무혼의 활성 곡을 종류별로 확인할 수 있어요.
          </p>
        </div>
        {isAdmin && (
          <Link href="/admin/songs" className="secondary-button">
            곡 / 팀 관리
          </Link>
        )}
      </section>

      <section className="app-card !p-4">
        <div className="flex flex-wrap items-center gap-2">
          <button
            type="button"
            className={
              stageTypeFilter === "ALL"
                ? "primary-button small-button"
                : "secondary-button small-button"
            }
            onClick={() => setStageTypeFilter("ALL")}
          >
            전체 {songs.length}
          </button>
          {stageTypes.map((stageType) => {
            const count = songs.filter(
              (song) => song.stageTypeName === stageType,
            ).length;
            return (
              <button
                key={stageType}
                type="button"
                className={
                  stageTypeFilter === stageType
                    ? "primary-button small-button"
                    : "secondary-button small-button"
                }
                onClick={() => setStageTypeFilter(stageType)}
              >
                {stageType} {count}
              </button>
            );
          })}
        </div>

        <div className="mt-4 flex items-end gap-2">
          <label className="field-label min-w-36">
            정렬
            <select
              className="field-input"
              value={sortKey}
              onChange={(event) =>
                setSortKey(event.target.value as SongSortKey)
              }
            >
              <option value="LATEST">최신순</option>
              <option value="NAME">이름순</option>
              <option value="STAGE">종류순</option>
            </select>
          </label>
          <button
            type="button"
            className="secondary-button h-[46px] min-w-12 px-3 text-lg"
            aria-label={
              sortDirection === "ASC"
                ? "정렬 역순으로 변경"
                : "정렬 정순으로 변경"
            }
            title={sortDirection === "ASC" ? "오름차순" : "내림차순"}
            onClick={() =>
              setSortDirection((current) =>
                current === "ASC" ? "DESC" : "ASC",
              )
            }
          >
            {sortDirection === "ASC" ? "↑" : "↓"}
          </button>
        </div>
      </section>

      {songsQuery.isPending && (
        <div className="app-card text-sm text-slate-500">
          곡 정보를 불러오고 있어요.
        </div>
      )}

      {songsQuery.isError && (
        <div className="error-box">{errorMessage(songsQuery.error)}</div>
      )}

      {!songsQuery.isPending && visibleSongs.length === 0 && (
        <div className="app-card text-center text-sm font-semibold text-slate-600">
          표시할 곡이 없습니다.
        </div>
      )}

      <div className="grid gap-4 md:grid-cols-2">
        {visibleSongs.map((song) => {
          const leader = song.members.find((member) => member.leader);
          return (
            <article key={song.id} className="app-card">
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0">
                  <h2 className="truncate text-xl font-black text-slate-950">
                    {song.title}
                  </h2>
                  <p className="mt-1 text-sm text-slate-500">
                    팀장 {leader ? `${leader.name} · ${leader.sessionName}` : "미지정"}
                  </p>
                </div>
                <div className="flex shrink-0 flex-wrap justify-end gap-2">
                  <span className="count-badge">{song.stageTypeName}</span>
                  <span className="count-badge">{song.members.length}명</span>
                </div>
              </div>

              <div className="mt-5 divide-y divide-slate-100 border-t border-slate-100">
                {song.members.map((member) => (
                  <div
                    key={member.userId}
                    className="flex items-center justify-between gap-3 py-3"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-bold text-slate-900">
                        {member.name}
                        {member.leader && (
                          <span className="ml-2 text-xs font-extrabold text-slate-500">
                            팀장
                          </span>
                        )}
                      </p>
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

function compareSongText(first: string, second: string) {
  const firstValue = first.trim();
  const secondValue = second.trim();
  const firstGroup = /^[A-Za-z]/.test(firstValue) ? 0 : 1;
  const secondGroup = /^[A-Za-z]/.test(secondValue) ? 0 : 1;
  if (firstGroup !== secondGroup) return firstGroup - secondGroup;
  return firstValue.localeCompare(secondValue, "ko-KR", {
    numeric: true,
    sensitivity: "base",
  });
}
