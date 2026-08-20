"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { AuthUser } from "@/lib/api";

export function AppShell({
  user,
  children,
}: {
  user: AuthUser;
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const isAdmin = user.role === "ADMIN" || user.role === "SUPER_ADMIN";

  const navItems = [
    { href: "/", label: "홈" },
    { href: "/schedule", label: "일정" },
    { href: "/songs", label: "곡" },
    { href: "/announcements", label: "공지" },
    ...(isAdmin ? [{ href: "/admin", label: "관리자" }] : []),
    { href: "/my", label: "MY" },
  ];

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
            return (
              <Link
                key={item.href}
                href={item.href}
                className={`flex items-center justify-center text-sm font-semibold transition ${
                  active ? "text-slate-950" : "text-slate-400"
                }`}
              >
                {item.label}
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
