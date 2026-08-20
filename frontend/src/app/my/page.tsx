"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthGate } from "@/components/auth-gate";
import { AppShell, roleLabel } from "@/components/app-shell";
import { authApi, errorMessage } from "@/lib/api";

export default function MyPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <MyContent
            userName={user.name}
            loginId={user.loginId}
            role={user.role}
          />
        </AppShell>
      )}
    </AuthGate>
  );
}

function MyContent({
  userName,
  loginId,
  role,
}: {
  userName: string;
  loginId: string;
  role: "MEMBER" | "ADMIN" | "SUPER_ADMIN";
}) {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [currentPassword, setCurrentPassword] = useState("");

  const logoutMutation = useMutation({
    mutationFn: authApi.logout,
    onSuccess: () => finishSession(),
  });

  const deleteMutation = useMutation({
    mutationFn: authApi.deleteMe,
    onSuccess: () => finishSession(),
  });

  function finishSession() {
    queryClient.clear();
    router.replace("/login");
  }

  function handleDelete(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!window.confirm("탈퇴하면 로그인할 수 없습니다. 정말 탈퇴할까요?")) {
      return;
    }
    deleteMutation.mutate(currentPassword);
  }

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">MY</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">내 계정</h1>
      </section>

      <section className="app-card">
        <dl className="space-y-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">이름</dt>
            <dd className="font-semibold text-slate-950">{userName}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">아이디</dt>
            <dd className="font-semibold text-slate-950">{loginId}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="text-slate-500">권한</dt>
            <dd className="font-semibold text-slate-950">{roleLabel(role)}</dd>
          </div>
        </dl>

        <button
          className="secondary-button mt-6 w-full"
          type="button"
          disabled={logoutMutation.isPending}
          onClick={() => logoutMutation.mutate()}
        >
          {logoutMutation.isPending ? "로그아웃 중..." : "로그아웃"}
        </button>
        {logoutMutation.isError && (
          <p className="error-box mt-3">{errorMessage(logoutMutation.error)}</p>
        )}
      </section>

      <section className="app-card border-red-100">
        <p className="card-label text-red-500">계정 탈퇴</p>
        {role === "SUPER_ADMIN" ? (
          <p className="mt-3 text-sm leading-6 text-slate-500">
            SUPER_ADMIN 계정은 서비스 소유자 계정이므로 탈퇴할 수 없습니다.
          </p>
        ) : (
          <form className="mt-4 space-y-3" onSubmit={handleDelete}>
            <p className="text-sm leading-6 text-slate-500">
              현재 비밀번호를 다시 입력해야 탈퇴할 수 있습니다. 과거 합주 이력의 참조는 유지되고 계정은 익명화됩니다.
            </p>
            <input
              className="field-input"
              type="password"
              value={currentPassword}
              onChange={(event) => setCurrentPassword(event.target.value)}
              minLength={8}
              maxLength={72}
              autoComplete="current-password"
              placeholder="현재 비밀번호"
              required
            />
            {deleteMutation.isError && (
              <p className="error-box">{errorMessage(deleteMutation.error)}</p>
            )}
            <button
              className="danger-button w-full"
              type="submit"
              disabled={deleteMutation.isPending}
            >
              {deleteMutation.isPending ? "탈퇴 처리 중..." : "회원 탈퇴"}
            </button>
          </form>
        )}
      </section>
    </div>
  );
}
