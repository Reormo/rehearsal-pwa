"use client";

import { useQuery } from "@tanstack/react-query";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { ApiError, AuthUser, authApi } from "@/lib/api";

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
  });

  useEffect(() => {
    if (
      meQuery.error instanceof ApiError &&
      meQuery.error.status === 401
    ) {
      router.replace("/login");
    }
  }, [meQuery.error, router]);

  if (meQuery.isPending) {
    return <FullScreenMessage>로그인 상태를 확인하고 있어요.</FullScreenMessage>;
  }

  if (meQuery.isError) {
    if (meQuery.error instanceof ApiError && meQuery.error.status === 401) {
      return <FullScreenMessage>로그인 화면으로 이동하고 있어요.</FullScreenMessage>;
    }
    return (
      <FullScreenMessage>
        사용자 정보를 불러오지 못했습니다. 잠시 후 다시 시도해주세요.
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
