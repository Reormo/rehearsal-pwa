"use client";

import Link from "next/link";
import { FormEvent, useId, useMemo, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { Member, Song, adminApi, errorMessage } from "@/lib/api";

const SESSION_PRESETS = ["보컬", "기타", "베이스", "드럼", "키보드", "신디사이저"];

export default function AdminSongsPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminSongsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminSongsContent() {
  const queryClient = useQueryClient();
  const [showArchived, setShowArchived] = useState(false);
  const [songSearch, setSongSearch] = useState("");
  const [title, setTitle] = useState("");
  const [leaderSearch, setLeaderSearch] = useState("");
  const [leaderSession, setLeaderSession] = useState("");
  const [createError, setCreateError] = useState("");
  const [creating, setCreating] = useState(false);

  const songsQuery = useQuery({
    queryKey: ["admin", "songs"],
    queryFn: adminApi.songs,
  });
  const membersQuery = useQuery({
    queryKey: ["admin", "members"],
    queryFn: adminApi.members,
  });

  const members = useMemo(() => membersQuery.data ?? [], [membersQuery.data]);
  const selectedLeader = useMemo(
    () => members.find((member) => memberOptionValue(member) === leaderSearch),
    [leaderSearch, members],
  );

  const refreshSongs = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "songs"] }),
      queryClient.invalidateQueries({ queryKey: ["songs", "mine"] }),
    ]);
  };

  async function createSong(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setCreateError("");
    if (!title.trim() || !selectedLeader || !leaderSession.trim()) {
      setCreateError("곡 제목, 최초 팀장, 팀장 세션을 모두 입력해주세요. 팀장은 검색 결과에서 선택해야 합니다.");
      return;
    }

    setCreating(true);
    try {
      await adminApi.createSong({
        title: title.trim(),
        leaderUserId: selectedLeader.userId,
        leaderSessionName: leaderSession.trim(),
      });
      setTitle("");
      setLeaderSearch("");
      setLeaderSession("");
      await refreshSongs();
    } catch (error) {
      setCreateError(errorMessage(error));
    } finally {
      setCreating(false);
    }
  }

  const visibleSongs = useMemo(() => {
    const byStatus = (songsQuery.data ?? []).filter((song) =>
      showArchived ? song.status === "ARCHIVED" : song.status === "ACTIVE",
    );
    if (showArchived) return byStatus;

    const query = songSearch.trim().toLocaleLowerCase("ko-KR");
    if (!query) return byStatus;
    return byStatus.filter((song) =>
      song.title.toLocaleLowerCase("ko-KR").includes(query),
    );
  }, [showArchived, songSearch, songsQuery.data]);

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="eyebrow">ADMIN · SONGS</p>
          <h1 className="mt-2 text-2xl font-black tracking-tight text-slate-950">곡 / 팀 관리</h1>
          <p className="mt-2 text-sm leading-6 text-slate-500">
            곡을 만들고 참여자, 세션, 팀장을 관리합니다. 활성 곡에는 항상 팀장이 한 명 있어야 해요.
          </p>
        </div>
        <Link href="/songs" className="secondary-button">
          사용자 화면 보기
        </Link>
      </section>

      <section className="app-card">
        <div className="mb-5">
          <p className="card-label">NEW SONG</p>
          <h2 className="mt-2 text-lg font-black text-slate-950">새 곡 만들기</h2>
        </div>

        <form className="grid gap-4 lg:grid-cols-4" onSubmit={createSong}>
          <label className="field-label lg:col-span-2">
            곡 제목
            <input
              className="field-input"
              value={title}
              maxLength={150}
              onChange={(event) => setTitle(event.target.value)}
              placeholder="예: 아지랑이"
            />
          </label>

          <MemberSearchField
            members={members}
            value={leaderSearch}
            onChange={setLeaderSearch}
            label="최초 팀장"
            placeholder="이름 또는 아이디 검색"
          />

          <SessionField value={leaderSession} onChange={setLeaderSession} label="팀장 세션" />

          {createError && <div className="error-box lg:col-span-4">{createError}</div>}

          <div className="lg:col-span-4">
            <button className="primary-button" disabled={creating || membersQuery.isPending}>
              {creating ? "생성 중..." : "곡 생성"}
            </button>
          </div>
        </form>
      </section>

      <section className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div className="flex gap-2">
          <button
            className={showArchived ? "secondary-button small-button" : "primary-button small-button"}
            onClick={() => setShowArchived(false)}
          >
            활성 곡 {songsQuery.data?.filter((song) => song.status === "ACTIVE").length ?? 0}
          </button>
          <button
            className={showArchived ? "primary-button small-button" : "secondary-button small-button"}
            onClick={() => setShowArchived(true)}
          >
            보관 곡 {songsQuery.data?.filter((song) => song.status === "ARCHIVED").length ?? 0}
          </button>
        </div>

        {!showArchived && (
          <label className="field-label w-full sm:max-w-xs">
            활성 곡 검색
            <input
              className="field-input"
              type="search"
              value={songSearch}
              onChange={(event) => setSongSearch(event.target.value)}
              placeholder="곡 제목 검색"
            />
          </label>
        )}
      </section>

      {(songsQuery.isError || membersQuery.isError) && (
        <div className="error-box">
          {errorMessage(songsQuery.error ?? membersQuery.error)}
        </div>
      )}

      {songsQuery.isPending && (
        <div className="app-card text-sm text-slate-500">곡 정보를 불러오고 있어요.</div>
      )}

      {visibleSongs.length === 0 && !songsQuery.isPending && (
        <div className="app-card text-center text-sm font-semibold text-slate-600">
          {showArchived
            ? "보관된 곡이 없습니다."
            : songSearch.trim()
              ? "검색 결과가 없습니다."
              : "활성 곡이 없습니다."}
        </div>
      )}

      <div className="space-y-4">
        {visibleSongs.map((song) => (
          <SongAdminCard
            key={song.id}
            song={song}
            members={members}
            refreshSongs={refreshSongs}
          />
        ))}
      </div>
    </div>
  );
}

function SongAdminCard({
  song,
  members,
  refreshSongs,
}: {
  song: Song;
  members: Member[];
  refreshSongs: () => Promise<void>;
}) {
  const [actionError, setActionError] = useState("");
  const [pending, setPending] = useState("");
  const [newMemberSearch, setNewMemberSearch] = useState("");
  const [newMemberSession, setNewMemberSession] = useState("");
  const active = song.status === "ACTIVE";
  const availableMembers = members.filter(
    (member) => !song.members.some((songMember) => songMember.userId === member.userId),
  );
  const selectedNewMember = availableMembers.find(
    (member) => memberOptionValue(member) === newMemberSearch,
  );

  async function run(key: string, action: () => Promise<unknown>) {
    setActionError("");
    setPending(key);
    try {
      await action();
      await refreshSongs();
    } catch (error) {
      setActionError(errorMessage(error));
    } finally {
      setPending("");
    }
  }

  async function rename() {
    const nextTitle = window.prompt("새 곡 제목", song.title)?.trim();
    if (!nextTitle || nextTitle === song.title) return;
    await run("rename", () => adminApi.renameSong(song.id, nextTitle));
  }

  async function changeSession(userId: number, current: string) {
    const nextSession = window.prompt("새 세션", current)?.trim();
    if (!nextSession || nextSession === current) return;
    await run(`session-${userId}`, () =>
      adminApi.changeSongMemberSession(song.id, userId, nextSession),
    );
  }

  async function addMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selectedNewMember || !newMemberSession.trim()) {
      setActionError("추가할 회원을 검색 결과에서 선택하고 세션을 입력해주세요.");
      return;
    }
    await run("add-member", () =>
      adminApi.addSongMember(song.id, selectedNewMember.userId, newMemberSession.trim()),
    );
    setNewMemberSearch("");
    setNewMemberSession("");
  }

  return (
    <article className="app-card">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <div className="flex flex-wrap items-center gap-2">
            <span className="card-label">{song.status}</span>
            <span className="count-badge">{song.members.length}명</span>
          </div>
          <h2 className="mt-2 text-xl font-black text-slate-950">{song.title}</h2>
        </div>
        <div className="flex flex-wrap gap-2">
          <button className="secondary-button small-button" onClick={rename} disabled={!!pending}>
            제목 수정
          </button>
          {active ? (
            <button
              className="danger-button small-button"
              disabled={!!pending}
              onClick={() => run("archive", () => adminApi.archiveSong(song.id))}
            >
              보관
            </button>
          ) : (
            <button
              className="primary-button small-button"
              disabled={!!pending}
              onClick={() => run("restore", () => adminApi.restoreSong(song.id))}
            >
              복구
            </button>
          )}
        </div>
      </div>

      {actionError && <div className="error-box mt-4">{actionError}</div>}

      <div className="mt-5 overflow-hidden rounded-2xl border border-slate-200">
        {song.members.map((member) => (
          <div
            key={member.userId}
            className="flex flex-col gap-3 border-b border-slate-100 bg-white p-4 last:border-b-0 sm:flex-row sm:items-center sm:justify-between"
          >
            <div className="min-w-0">
              <p className="font-bold text-slate-900">
                {member.name}
                {member.leader && (
                  <span className="ml-2 rounded-full bg-slate-900 px-2 py-1 text-[10px] font-black text-white">
                    팀장
                  </span>
                )}
              </p>
              <p className="mt-1 text-xs text-slate-500">
                {member.sessionName}{member.loginId ? ` · @${member.loginId}` : ""}
              </p>
            </div>

            {active && (
              <div className="flex flex-wrap gap-2">
                <button
                  className="secondary-button small-button"
                  disabled={!!pending}
                  onClick={() => changeSession(member.userId, member.sessionName)}
                >
                  세션 변경
                </button>
                {!member.leader && (
                  <>
                    <button
                      className="secondary-button small-button"
                      disabled={!!pending}
                      onClick={() =>
                        run(`leader-${member.userId}`, () =>
                          adminApi.changeSongLeader(song.id, member.userId),
                        )
                      }
                    >
                      팀장 지정
                    </button>
                    <button
                      className="danger-button small-button"
                      disabled={!!pending}
                      onClick={() => {
                        if (window.confirm(`${member.name} 님을 이 곡에서 제외할까요?`)) {
                          void run(`remove-${member.userId}`, () =>
                            adminApi.removeSongMember(song.id, member.userId),
                          );
                        }
                      }}
                    >
                      제외
                    </button>
                  </>
                )}
              </div>
            )}
          </div>
        ))}
      </div>

      {active && (
        <form className="mt-5 grid gap-3 sm:grid-cols-[1fr_1fr_auto]" onSubmit={addMember}>
          <MemberSearchField
            members={availableMembers}
            value={newMemberSearch}
            onChange={setNewMemberSearch}
            label="참여자 추가"
            placeholder={availableMembers.length === 0 ? "추가 가능한 회원 없음" : "이름 또는 아이디 검색"}
            disabled={availableMembers.length === 0}
          />
          <SessionField value={newMemberSession} onChange={setNewMemberSession} label="세션" />
          <div className="flex items-end">
            <button
              className="primary-button w-full sm:w-auto"
              disabled={!!pending || availableMembers.length === 0}
            >
              추가
            </button>
          </div>
        </form>
      )}
    </article>
  );
}

function MemberSearchField({
  members,
  value,
  onChange,
  label,
  placeholder,
  disabled = false,
}: {
  members: Member[];
  value: string;
  onChange: (value: string) => void;
  label: string;
  placeholder: string;
  disabled?: boolean;
}) {
  const listId = useId();

  return (
    <label className="field-label">
      {label}
      <input
        className="field-input"
        type="search"
        list={listId}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={placeholder}
        disabled={disabled}
        autoComplete="off"
      />
      <datalist id={listId}>
        {members.map((member) => (
          <option key={member.userId} value={memberOptionValue(member)} />
        ))}
      </datalist>
      <span className="field-help">이름이나 로그인 아이디를 입력해 검색한 뒤 회원을 선택하세요.</span>
    </label>
  );
}

function memberOptionValue(member: Member) {
  return `${member.name} (${member.loginId})`;
}

function SessionField({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (value: string) => void;
  label: string;
}) {
  const listId = useId();

  return (
    <label className="field-label">
      {label}
      <input
        className="field-input"
        list={listId}
        maxLength={50}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder="예: 기타"
      />
      <datalist id={listId}>
        {SESSION_PRESETS.map((session) => (
          <option key={session} value={session} />
        ))}
      </datalist>
      <span className="field-help">추천에서 선택하거나 직접 입력할 수 있어요.</span>
    </label>
  );
}
