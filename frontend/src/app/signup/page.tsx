"use client";

import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { FormEvent, useState } from "react";
import { authApi, errorMessage } from "@/lib/api";

export default function SignupPage() {
  const [inviteCode, setInviteCode] = useState("");
  const [loginId, setLoginId] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");

  const signupMutation = useMutation({ mutationFn: authApi.signup });

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    signupMutation.mutate({
      inviteCode: inviteCode.trim(),
      loginId: loginId.trim(),
      password,
      name: name.trim(),
    });
  }

  if (signupMutation.isSuccess) {
    return (
      <main className="auth-page">
        <section className="auth-card text-center">
          <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-emerald-100 text-xl">
            ✓
          </div>
          <h1 className="mt-5 text-2xl font-bold text-slate-950">가입 신청 완료</h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            관리자가 가입 신청을 승인하면 로그인할 수 있어요.
          </p>
          <p className="mt-2 text-xs text-slate-400">
            신청 번호 #{signupMutation.data.applicationId}
          </p>
          <Link href="/login" className="primary-button mt-7 inline-flex w-full">
            로그인 화면으로
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="auth-page py-10">
      <section className="auth-card">
        <p className="eyebrow">JOIN THE CLUB</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          가입 신청
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-500">
          초대코드를 입력해 신청한 뒤 관리자 승인을 기다려주세요.
        </p>

        <form className="mt-8 space-y-5" onSubmit={handleSubmit}>
          <label className="field-label">
            초대코드
            <input
              className="field-input"
              value={inviteCode}
              onChange={(event) => setInviteCode(event.target.value)}
              maxLength={100}
              required
            />
          </label>

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
              autoComplete="new-password"
              minLength={8}
              maxLength={72}
              required
            />
            <span className="field-help">8~72자로 입력해주세요.</span>
          </label>

          <label className="field-label">
            이름
            <input
              className="field-input"
              value={name}
              onChange={(event) => setName(event.target.value)}
              maxLength={50}
              required
            />
          </label>

          {signupMutation.isError && (
            <p className="error-box">{errorMessage(signupMutation.error)}</p>
          )}

          <button
            className="primary-button w-full"
            type="submit"
            disabled={signupMutation.isPending}
          >
            {signupMutation.isPending ? "신청 중..." : "가입 신청"}
          </button>
        </form>

        <p className="mt-7 text-center text-sm text-slate-500">
          이미 계정이 있나요?{" "}
          <Link href="/login" className="font-semibold text-slate-900 underline">
            로그인
          </Link>
        </p>
      </section>
    </main>
  );
}
