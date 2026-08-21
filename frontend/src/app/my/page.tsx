"use client";

import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";

const menuItems = [
  { href: "/my/account", label: "내 계정", icon: "account" },
  { href: "/my/notifications", label: "알림 설정", icon: "notification" },
  { href: "/my/teams", label: "내 팀", icon: "team" },
  { href: "/my/reservations", label: "합주 관리", icon: "rehearsal" },
  { href: "/my/swaps", label: "일정 교환", icon: "swap" },
] as const;

type MenuIconName = (typeof menuItems)[number]["icon"];

export default function MyPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <div className="space-y-7">
            <section>
              <p className="eyebrow">ALL</p>
              <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
                전체
              </h1>
            </section>

            <nav
              aria-label="전체 메뉴"
              className="overflow-hidden rounded-3xl border border-slate-200 bg-white shadow-[0_16px_50px_rgba(15,23,42,0.06)]"
            >
              {menuItems.map((item, index) => (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`flex min-h-[72px] items-center gap-4 px-5 transition-colors hover:bg-slate-50 active:bg-slate-100 ${
                    index > 0 ? "border-t border-slate-100" : ""
                  }`}
                >
                  <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-slate-100 text-slate-700">
                    <MenuIcon name={item.icon} />
                  </span>
                  <span className="min-w-0 flex-1 text-base font-bold text-slate-950">
                    {item.label}
                  </span>
                  <svg
                    className="h-5 w-5 shrink-0 text-slate-300"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="2"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    aria-hidden="true"
                  >
                    <path d="m9 18 6-6-6-6" />
                  </svg>
                </Link>
              ))}
            </nav>
          </div>
        </AppShell>
      )}
    </AuthGate>
  );
}

function MenuIcon({ name }: { name: MenuIconName }) {
  const common = {
    className: "h-5 w-5",
    viewBox: "0 0 24 24",
    fill: "none",
    stroke: "currentColor",
    strokeWidth: 2,
    strokeLinecap: "round" as const,
    strokeLinejoin: "round" as const,
    "aria-hidden": true,
  };

  if (name === "account") {
    return (
      <svg {...common}>
        <circle cx="12" cy="8" r="4" />
        <path d="M4.5 21a7.5 7.5 0 0 1 15 0" />
      </svg>
    );
  }

  if (name === "notification") {
    return (
      <svg {...common}>
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </svg>
    );
  }

  if (name === "team") {
    return (
      <svg {...common}>
        <circle cx="9" cy="8" r="3" />
        <circle cx="17" cy="9" r="2" />
        <path d="M3.5 20a5.5 5.5 0 0 1 11 0" />
        <path d="M14.5 15.5A4.5 4.5 0 0 1 20.5 20" />
      </svg>
    );
  }

  if (name === "rehearsal") {
    return (
      <svg {...common}>
        <rect x="3" y="5" width="18" height="16" rx="3" />
        <path d="M7 3v4M17 3v4M3 10h18" />
        <path d="m8.5 15 2.2 2.2 4.8-5" />
      </svg>
    );
  }

  return (
    <svg {...common}>
      <path d="M7 7h11l-3-3" />
      <path d="m18 7-3 3" />
      <path d="M17 17H6l3 3" />
      <path d="m6 17 3-3" />
    </svg>
  );
}
