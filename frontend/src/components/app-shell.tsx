"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { AuthUser } from "@/lib/api";
import { notificationApi } from "@/lib/notification-api";

type NavIconName =
  | "home"
  | "reservation"
  | "announcement"
  | "admin"
  | "menu"
  | "bell";

export function AppShell({
  user,
  children,
}: {
  user: AuthUser;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";
  const unreadCountQuery = useQuery({
    queryKey: ["notifications", "unread-count"],
    queryFn: notificationApi.unreadCount,
    refetchInterval: 30_000,
  });
  const unreadCount = unreadCountQuery.data?.count ?? 0;

  const navItems: Array<{
    href: string;
    label: string;
    icon: NavIconName;
  }> = [
    { href: "/", label: "홈", icon: "home" },
    { href: "/schedule", label: "예약", icon: "reservation" },
    { href: "/announcements", label: "공지", icon: "announcement" },
    ...(isAdmin
      ? [{ href: "/admin", label: "관리자", icon: "admin" as NavIconName }]
      : []),
    { href: "/my", label: "전체", icon: "menu" },
  ];

  return (
    <div className="min-h-dvh bg-slate-50">
      <header className="sticky top-0 z-20 border-b border-slate-200 bg-white/95 pt-[env(safe-area-inset-top)] backdrop-blur">
        <div className="mx-auto flex h-16 max-w-5xl items-center justify-between px-5">
          <div className="min-w-0">
            <p className="truncate text-sm font-bold text-slate-950">{user.name}</p>
            <p className="mt-0.5 text-xs font-medium text-slate-500">
              {roleLabel(user.role)}
            </p>
          </div>

          <Link
            href="/notifications"
            aria-label={
              unreadCount > 0
                ? `알림 ${unreadCount > 99 ? "99개 이상" : `${unreadCount}개`}`
                : "알림"
            }
            className="relative flex h-10 w-10 shrink-0 touch-manipulation items-center justify-center rounded-full text-slate-700 transition-[background-color,transform] duration-150 ease-out hover:bg-slate-100 active:scale-90"
          >
            <NavIcon name="bell" className="h-7 w-7" />
            {unreadCount > 0 && (
              <span className="absolute -right-1 -top-1 inline-flex min-w-5 items-center justify-center rounded-full bg-red-500 px-1.5 py-1 text-[10px] font-black leading-none text-white shadow-sm">
                {unreadCount > 99 ? "99+" : unreadCount}
              </span>
            )}
          </Link>
        </div>
      </header>

      <main className="mx-auto w-full max-w-5xl px-5 py-7 pb-32">{children}</main>

      <nav className="fixed inset-x-0 bottom-0 z-30 rounded-t-[28px] border-t border-slate-200 bg-white/95 pb-[env(safe-area-inset-bottom)] shadow-[0_-10px_30px_rgba(15,23,42,0.06)] backdrop-blur">
        <div
          className="mx-auto grid h-20 max-w-md px-3"
          style={{
            gridTemplateColumns: `repeat(${navItems.length}, minmax(0, 1fr))`,
          }}
        >
          {navItems.map((item) => {
            const active =
              item.href === "/"
                ? pathname === "/"
                : pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`flex h-20 min-w-0 touch-manipulation select-none flex-col items-center justify-center gap-0.5 transition-[color,transform] duration-150 ease-out active:scale-90 ${
                  active ? "text-slate-950" : "text-slate-400"
                }`}
              >
                <span className="flex h-9 w-9 shrink-0 items-center justify-center">
                  <NavIcon name={item.icon} className="h-7 w-7" />
                </span>
                <span className="h-4 text-[11px] font-bold leading-4">
                  {item.label}
                </span>
              </Link>
            );
          })}
        </div>
      </nav>
    </div>
  );
}

function NavIcon({
  name,
  className,
}: {
  name: NavIconName;
  className?: string;
}) {
  const common = {
    className,
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  if (name === "home") {
    return (
      <svg {...common}>
        <path d="M3 10.8 12 3l9 7.8" />
        <path d="M5.5 9.5V21h13V9.5" />
        <path d="M9.5 21v-6h5v6" />
      </svg>
    );
  }

  if (name === "reservation") {
    return (
      <svg {...common}>
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M7 3v4M17 3v4M3 10h18" />
        <path d="m8.5 15 2.2 2.2 4.8-5" />
      </svg>
    );
  }

  if (name === "announcement") {
    return (
      <svg {...common}>
        <path d="M4 13V9a2 2 0 0 1 2-2h3l8-4v16l-8-4H6a2 2 0 0 1-2-2Z" />
        <path d="m9 15 1.5 5H7l-1.5-5M20 8v6" />
      </svg>
    );
  }

  if (name === "admin") {
    return (
      <svg {...common}>
        <path d="M12 3 20 6v5c0 5-3.4 8.4-8 10-4.6-1.6-8-5-8-10V6l8-3Z" />
        <circle cx="12" cy="10" r="2.2" />
        <path d="M8.5 16c.8-1.7 2-2.5 3.5-2.5s2.7.8 3.5 2.5" />
      </svg>
    );
  }

  if (name === "menu") {
    return (
      <svg {...common}>
        <circle cx="5" cy="5" r="1.5" />
        <circle cx="12" cy="5" r="1.5" />
        <circle cx="19" cy="5" r="1.5" />
        <circle cx="5" cy="12" r="1.5" />
        <circle cx="12" cy="12" r="1.5" />
        <circle cx="19" cy="12" r="1.5" />
        <circle cx="5" cy="19" r="1.5" />
        <circle cx="12" cy="19" r="1.5" />
        <circle cx="19" cy="19" r="1.5" />
      </svg>
    );
  }

  if (name === "bell") {
    return (
      <svg {...common}>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </svg>
    );
  }

  return (
    <svg {...common}>
      <circle cx="12" cy="8" r="4" />
      <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
    </svg>
  );
}

export function roleLabel(role: AuthUser["role"]) {
  if (role === "SUPER_ADMIN") return "최고 관리자";
  if (role === "ADMIN") return "관리자";
  return "회원";
}
