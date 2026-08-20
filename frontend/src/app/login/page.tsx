"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { AuthUser, authApi, errorMessage } from "@/lib/api";

export default function LoginPage() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");

  const loginMutation = useMutation({
    mutationFn: authApi.login,
    onSuccess: (user) => {
      queryClient.setQueryData<AuthUser>(["auth", "me"], user);
      router.replace("/");
    },
  });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    loginMutation.mutate({ loginId: loginId.trim(), password });
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div>
          <p className="eyebrow">BAND REHEARSAL</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            합주 예약 로그인
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            승인된 동아리 계정으로 로그인해주세요.
          </p>
        </div>

        <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
          <label className="field-label">
            아이디
            <input
              className="field-input"
              value={loginId}
              onChange={(event) => setLoginId(event.target.value)}
              autoComplete="username"
              minLength={4}
              maxLength={50}
              required
            />
          </label>

          <label className="field-label">
            비밀번호
            <input
              className="field-input"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              autoComplete="current-password"
              minLength={8}
              maxLength={72}
              required
            />
          </label>

          {loginMutation.isError && (
            <p className="error-box">{errorMessage(loginMutation.error)}</p>
          )}

          <button
            className="primary-button w-full"
            type="submit"
            disabled={loginMutation.isPending}
          >
            {loginMutation.isPending ? "로그인 중..." : "로그인"}
          </button>
        </form>

        <p className="mt-7 text-center text-sm text-slate-500">
          아직 계정이 없나요?{" "}
          <Link href="/signup" className="font-semibold text-slate-900 underline">
            가입 신청
          </Link>
        </p>
      </section>
    </main>
  );
}
