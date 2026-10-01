"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ApiError, AuthUser, authApi, errorMessage } from "@/lib/api";

export function AuthGate({
  children,
  adminOnly = false,
}: {
  children: (user: AuthUser) => React.ReactNode;
  adminOnly?: boolean;
}) {
  const router = useRouter();
  const meQuery = useQuery({
    queryKey: ["auth", "me"],
    queryFn: authApi.me,
    networkMode: "always",
  });

  useEffect(() => {
    if (
      meQuery.error instanceof ApiError &&
      meQuery.error.status === 401
    ) {
      router.replace("/login");
    }
  }, [meQuery.error, router]);

  if (meQuery.isLoading) {
    return <FullScreenMessage>로그인 상태를 확인하고 있어요.</FullScreenMessage>;
  }

  if (meQuery.isPending) {
    return (
      <FullScreenMessage>
        <p>로그인 상태 확인 요청을 시작하지 못했습니다.</p>
        <button
          type="button"
          className="secondary-button mt-4"
          onClick={() => void meQuery.refetch()}
        >
          다시 시도
        </button>
      </FullScreenMessage>
    );
  }

  if (meQuery.isError) {
    if (meQuery.error instanceof ApiError && meQuery.error.status === 401) {
      return <FullScreenMessage>로그인 화면으로 이동하고 있어요.</FullScreenMessage>;
    }
    return (
      <FullScreenMessage>
        <p>{errorMessage(meQuery.error)}</p>
        <div className="mt-4 flex flex-wrap justify-center gap-2">
          <button
            type="button"
            className="secondary-button"
            onClick={() => void meQuery.refetch()}
          >
            다시 시도
          </button>
          <button
            type="button"
            className="secondary-button"
            onClick={() => router.replace("/login")}
          >
            로그인 화면
          </button>
        </div>
      </FullScreenMessage>
    );
  }

  const user = meQuery.data;
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";

  if (adminOnly && !isAdmin) {
    return <FullScreenMessage>관리자만 접근할 수 있는 화면입니다.</FullScreenMessage>;
  }

  return <>{children(user)}</>;
}

function FullScreenMessage({ children }: { children: React.ReactNode }) {
  return (
    <main className="flex min-h-screen items-center justify-center px-6">
      <div className="app-card max-w-md text-center text-sm text-slate-600">
        {children}
      </div>
    </main>
  );
}
