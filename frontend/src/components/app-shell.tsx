"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AuthUser } from "@/lib/api";
import { notificationApi } from "@/lib/notification-api";

export function AppShell({
  user,
  children,
}: {
  user: AuthUser;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const queryClient = useQueryClient();
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
  const unreadCountQuery = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: notificationApi.unreadCount,
    refetchInterval: 30_000,
  });
  const unreadCount = unreadCountQuery.data?.count ?? 0;

  const navItems = [
    { href: "/", label: "홈" },
    { href: "/schedule", label: "일정" },
    { href: "/songs", label: "곡" },
    { href: "/announcements", label: "공지" },
    { href: "/notifications", label: "알림" },
    ...(isAdmin ? [{ href: "/admin", label: "관리자" }] : []),
    { href: "/my", label: "MY" },
  ];

  function acknowledgeNotifications() {
    if (unreadCount <= 0) return;

    queryClient.setQueryData(["notifications", "unread-count"], { count: 0 });
    void notificationApi.markAllRead().catch(() => {
      void queryClient.invalidateQueries({
        queryKey: ["notifications", "unread-count"],
      });
    });
  }

  return (
    <div className="min-h-screen bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-5">
          <Link href="/" className="font-bold tracking-tight text-slate-950">
            합주 예약
          </Link>
          <div className="text-right">
            <p className="text-sm font-semibold text-slate-900">{user.name}</p>
            <p className="text-xs text-slate-500">{roleLabel(user.role)}</p>
          </div>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-5 py-7 pb-24">{children}</main>

      <nav className="fixed inset-x-0 bottom-0 z-20 border-t border-slate-200 bg-white">
        <div
          className="mx-auto grid h-16 max-w-md px-2"
          style={{ gridTemplateColumns: `repeat(${navItems.length}, minmax(0, 1fr))` }}
        >
          {navItems.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            const notificationItem = item.href === "/notifications";
            return (
              <Link
                key={item.href}
                href={item.href}
                onClick={notificationItem ? acknowledgeNotifications : undefined}
                className={`flex items-center justify-center text-sm font-semibold transition ${
                  active ? "text-slate-950" : "text-slate-400"
                }`}
              >
                <span className="inline-flex items-center gap-1">
                  {item.label}
                  {notificationItem && unreadCount > 0 && (
                    <span className="inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-0.5 text-[10px] font-bold leading-none text-white">
                      {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                  )}
                </span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}

export function roleLabel(role: AuthUser["role"]) {
  if (role === "SUPER_ADMIN") return "최고 관리자";
  if (role === "ADMIN") return "관리자";
  return "회원";
}
