"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useMemo, useState } from "react";
import { AuthGate } from "@/components/auth-gate";
import { AppShell, roleLabel } from "@/components/app-shell";
import {
  adminApi,
  ClubRole,
  errorMessage,
  Member,
  Song,
  SignupApplication,
} from "@/lib/api";

export default function AdminPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminContent currentUserId={user.id} currentRole={user.role} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminContent({
  currentUserId,
  currentRole,
}: {
  currentUserId: number;
  currentRole: ClubRole;
}) {
  const queryClient = useQueryClient();
  const [memberSearch, setMemberSearch] = useState("");

  const inviteQuery = useQuery({
    queryKey: ["admin", "invite-code"],
    queryFn: adminApi.inviteCode,
  });
  const applicationsQuery = useQuery({
    queryKey: ["admin", "signup-applications", "PENDING"],
    queryFn: () => adminApi.signupApplications("PENDING"),
  });
  const membersQuery = useQuery({
    queryKey: ["admin", "members"],
    queryFn: adminApi.members,
  });
  const songsQuery = useQuery({
    queryKey: ["admin", "songs"],
    queryFn: adminApi.songs,
  });

  const filteredMembers = useMemo(() => {
    const query = memberSearch.trim().toLocaleLowerCase("ko-KR");
    if (!query) return membersQuery.data ?? [];

    return (membersQuery.data ?? []).filter((member) => {
      const name = member.name.toLocaleLowerCase("ko-KR");
      const loginId = (member.loginId ?? "").toLocaleLowerCase("ko-KR");
      return name.includes(query) || loginId.includes(query);
    });
  }, [memberSearch, membersQuery.data]);

  const rotateMutation = useMutation({
    mutationFn: adminApi.rotateInviteCode,
    onSuccess: (data) => {
      queryClient.setQueryData(["admin", "invite-code"], data);
    },
  });

  const approveMutation = useMutation({
    mutationFn: adminApi.approveSignup,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["admin", "signup-applications", "PENDING"],
        }),
        queryClient.invalidateQueries({ queryKey: ["admin", "members"] }),
      ]);
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      adminApi.rejectSignup(id, reason),
    onSuccess: () =>
      queryClient.invalidateQueries({
        queryKey: ["admin", "signup-applications", "PENDING"],
      }),
  });

  const roleMutation = useMutation({
    mutationFn: ({ userId, role }: { userId: number; role: ClubRole }) =>
      adminApi.changeRole(userId, role),
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["admin", "members"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: adminApi.deleteMember,
    onSuccess: () =>
      queryClient.invalidateQueries({ queryKey: ["admin", "members"] }),
  });

  const resetPasswordMutation = useMutation({
    mutationFn: ({ userId, password }: { userId: number; password: string }) =>
      adminApi.resetPassword(userId, password),
  });

  const mutationError =
    rotateMutation.error ??
    approveMutation.error ??
    rejectMutation.error ??
    roleMutation.error ??
    deleteMutation.error ??
    resetPasswordMutation.error;

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">ADMIN</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          관리자
        </h1>
        <p className="mt-3 text-sm text-slate-500">
          회원, 곡, 공지, 합주 운영과 관리자 작업 이력을 한곳에서 관리합니다.
        </p>
      </section>

      <section className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <AdminMenuCard
          href="/admin/songs"
          title="곡 / 팀 관리"
          description="곡, 참여자, 세션과 팀장을 관리합니다."
        />
        <AdminMenuCard
          href="/admin/schedule"
          title="합주 운영"
          description="예약 기본값, 회차와 동아리방 예외 날짜를 관리합니다."
        />
        <AdminMenuCard
          href="/admin/announcements"
          title="공지 관리"
          description="공지 작성, 수정, 고정과 삭제를 관리합니다."
        />
        <AdminMenuCard
          href="/admin/logs"
          title="작업 이력"
          description="관리자 변경 작업의 감사 로그를 확인합니다."
        />
      </section>

      {mutationError && <p className="error-box">{errorMessage(mutationError)}</p>}

      <section className="app-card">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <p className="card-label">현재 초대코드</p>
            {inviteQuery.isPending ? (
              <p className="mt-3 text-sm text-slate-400">불러오는 중...</p>
            ) : inviteQuery.isError ? (
              <p className="mt-3 text-sm text-red-600">{errorMessage(inviteQuery.error)}</p>
            ) : (
              <>
                <p className="mt-3 break-all font-mono text-lg font-bold text-slate-950">
                  {inviteQuery.data.code}
                </p>
                <p className="mt-1 text-xs text-slate-400">
                  발급 {formatDate(inviteQuery.data.createdAt)}
                </p>
              </>
            )}
          </div>
          <button
            className="secondary-button"
            type="button"
            disabled={rotateMutation.isPending}
            onClick={() => {
              if (window.confirm("기존 초대코드는 즉시 사용할 수 없게 됩니다. 재발급할까요?")) {
                rotateMutation.mutate();
              }
            }}
          >
            {rotateMutation.isPending ? "재발급 중..." : "초대코드 재발급"}
          </button>
        </div>
      </section>

      <section className="app-card">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="card-label">가입 신청</p>
            <h2 className="mt-2 text-lg font-bold text-slate-950">승인 대기</h2>
          </div>
          <span className="count-badge">{applicationsQuery.data?.length ?? 0}</span>
        </div>

        <div className="mt-5 space-y-3">
          {applicationsQuery.isPending && <EmptyText>불러오는 중...</EmptyText>}
          {applicationsQuery.isError && (
            <p className="text-sm text-red-600">{errorMessage(applicationsQuery.error)}</p>
          )}
          {applicationsQuery.data?.length === 0 && (
            <EmptyText>대기 중인 가입 신청이 없습니다.</EmptyText>
          )}
          {applicationsQuery.data?.map((application) => (
            <SignupRow
              key={application.id}
              application={application}
              disabled={approveMutation.isPending || rejectMutation.isPending}
              onApprove={() => approveMutation.mutate(application.id)}
              onReject={(reason) =>
                rejectMutation.mutate({ id: application.id, reason })
              }
            />
          ))}
        </div>
      </section>

      <section className="app-card">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div>
            <p className="card-label">회원</p>
            <h2 className="mt-2 text-lg font-bold text-slate-950">전체 회원</h2>
          </div>
          <span className="count-badge">{membersQuery.data?.length ?? 0}</span>
        </div>

        <label className="field-label mt-5 block">
          회원 이름 검색
          <input
            className="field-input mt-2"
            type="search"
            value={memberSearch}
            onChange={(event) => setMemberSearch(event.target.value)}
            placeholder="이름 검색"
          />
          <span className="field-help">
            이름을 입력해 회원을 찾고, 회원을 누르면 참여 중인 팀 현황을 볼 수 있습니다.
          </span>
        </label>

        <div className="mt-5 space-y-3">
          {membersQuery.isPending && <EmptyText>불러오는 중...</EmptyText>}
          {(membersQuery.isError || songsQuery.isError) && (
            <p className="text-sm text-red-600">
              {errorMessage(membersQuery.error ?? songsQuery.error)}
            </p>
          )}
          {!membersQuery.isPending && filteredMembers.length === 0 && (
            <EmptyText>검색 결과가 없습니다.</EmptyText>
          )}
          {filteredMembers.map((member) => (
            <MemberRow
              key={member.userId}
              member={member}
              songs={songsQuery.data ?? []}
              currentUserId={currentUserId}
              canChangeRole={currentRole === "SUPER_ADMIN"}
              busy={
                roleMutation.isPending ||
                deleteMutation.isPending ||
                resetPasswordMutation.isPending
              }
              onChangeRole={(role) =>
                roleMutation.mutate({ userId: member.userId, role })
              }
              onDelete={() => deleteMutation.mutate(member.userId)}
              onResetPassword={(password) =>
                resetPasswordMutation.mutate({ userId: member.userId, password })
              }
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function SignupRow({
  application,
  disabled,
  onApprove,
  onReject,
}: {
  application: SignupApplication;
  disabled: boolean;
  onApprove: () => void;
  onReject: (reason: string) => void;
}) {
  const [reason, setReason] = useState("");

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-semibold text-slate-950">{application.name}</p>
          <p className="mt-1 text-sm text-slate-500">{application.loginId}</p>
          <p className="mt-1 text-xs text-slate-400">{formatDate(application.createdAt)}</p>
        </div>
        <button
          className="primary-button small-button"
          type="button"
          disabled={disabled}
          onClick={onApprove}
        >
          승인
        </button>
      </div>
      <div className="mt-3 flex flex-col gap-2 sm:flex-row">
        <input
          className="field-input flex-1"
          value={reason}
          onChange={(event) => setReason(event.target.value)}
          maxLength={500}
          placeholder="거절 사유 (선택)"
        />
        <button
          className="danger-button small-button"
          type="button"
          disabled={disabled}
          onClick={() => onReject(reason.trim())}
        >
          거절
        </button>
      </div>
    </div>
  );
}

function MemberRow({
  member,
  songs,
  currentUserId,
  canChangeRole,
  busy,
  onChangeRole,
  onDelete,
  onResetPassword,
}: {
  member: Member;
  songs: Song[];
  currentUserId: number;
  canChangeRole: boolean;
  busy: boolean;
  onChangeRole: (role: ClubRole) => void;
  onDelete: () => void;
  onResetPassword: (password: string) => void;
}) {
  const [newPassword, setNewPassword] = useState("");
  const [teamsOpen, setTeamsOpen] = useState(false);
  const isSelf = member.userId === currentUserId;
  const isSuperAdmin = member.role === "SUPER_ADMIN";
  const teams = songs
    .filter((song) =>
      song.members.some((songMember) => songMember.userId === member.userId),
    )
    .sort((first, second) => {
      const statusOrder =
        Number(second.status === "ACTIVE") - Number(first.status === "ACTIVE");
      return statusOrder || first.title.localeCompare(second.title, "ko-KR");
    });
  const activeTeamCount = teams.filter((song) => song.status === "ACTIVE").length;

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <button
          type="button"
          className="min-w-0 text-left"
          aria-expanded={teamsOpen}
          onClick={() => setTeamsOpen((current) => !current)}
        >
          <p className="font-semibold text-slate-950">
            {member.name} {isSelf && <span className="text-xs text-slate-400">(나)</span>}
          </p>
          <p className="mt-1 text-sm text-slate-500">{member.loginId ?? "삭제된 계정"}</p>
          <p className="mt-1 text-xs font-semibold text-slate-400">
            {roleLabel(member.role)} · 활성 팀 {activeTeamCount}개 · 전체 {teams.length}개
            <span className="ml-2">{teamsOpen ? "▲" : "▼"}</span>
          </p>
        </button>

        {canChangeRole && !isSuperAdmin && (
          <select
            className="rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm font-semibold text-slate-700"
            value={member.role}
            disabled={busy}
            onChange={(event) => onChangeRole(event.target.value as ClubRole)}
          >
            <option value="MEMBER">회원</option>
            <option value="ADMIN">관리자</option>
          </select>
        )}
      </div>

      {teamsOpen && (
        <div className="mt-4 rounded-2xl bg-slate-50 p-4">
          <div className="flex items-center justify-between gap-3">
            <p className="text-sm font-bold text-slate-900">팀 현황</p>
            <span className="text-xs font-semibold text-slate-400">
              활성 {activeTeamCount} · 전체 {teams.length}
            </span>
          </div>

          {teams.length === 0 ? (
            <p className="mt-3 text-sm text-slate-500">참여 중인 팀이 없습니다.</p>
          ) : (
            <div className="mt-3 space-y-2">
              {teams.map((song) => {
                const songMember = song.members.find(
                  (item) => item.userId === member.userId,
                );
                if (!songMember) return null;

                return (
                  <div
                    key={song.id}
                    className="rounded-xl border border-slate-200 bg-white px-3 py-3"
                  >
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-bold text-slate-900">{song.title}</p>
                      <span className="count-badge">
                        {song.status === "ACTIVE" ? "활성" : "보관"}
                      </span>
                      <span className="count-badge">무대 · {song.stageTypeName}</span>
                      {songMember.leader && <span className="count-badge">팀장</span>}
                    </div>
                    <p className="mt-1 text-xs text-slate-500">
                      세션 · {songMember.sessionName}
                    </p>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {!isSuperAdmin && (
        <div className="mt-4 grid gap-2 sm:grid-cols-[1fr_auto_auto]">
          <input
            className="field-input"
            type="password"
            value={newPassword}
            onChange={(event) => setNewPassword(event.target.value)}
            minLength={8}
            maxLength={72}
            placeholder="새 비밀번호 (8~72자)"
          />
          <button
            className="secondary-button small-button"
            type="button"
            disabled={busy || newPassword.length < 8}
            onClick={() => {
              onResetPassword(newPassword);
              setNewPassword("");
            }}
          >
            비밀번호 초기화
          </button>
          <button
            className="danger-button small-button"
            type="button"
            disabled={busy || isSelf}
            onClick={() => {
              if (window.confirm(`${member.name} 회원을 삭제할까요?`)) {
                onDelete();
              }
            }}
          >
            회원 삭제
          </button>
        </div>
      )}
    </div>
  );
}

function AdminMenuCard({
  href,
  title,
  description,
}: {
  href: string;
  title: string;
  description: string;
}) {
  return (
    <Link
      href={href}
      className="app-card block transition hover:-translate-y-0.5 hover:border-slate-300"
    >
      <p className="font-bold text-slate-950">{title}</p>
      <p className="mt-2 text-sm leading-6 text-slate-500">{description}</p>
    </Link>
  );
}

function EmptyText({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
      {children}
    </p>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
