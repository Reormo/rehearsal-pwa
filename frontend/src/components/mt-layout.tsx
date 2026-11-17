"use client";

import Link from "next/link";
import { AuthGate } from "@/components/auth-gate";
import { AppShell } from "@/components/app-shell";
import { AuthUser } from "@/lib/api";

export function MtLayout({children,hostOnly=false}:{children:(user:AuthUser)=>React.ReactNode;hostOnly?:boolean}) {
  return (
    <AuthGate adminOnly={hostOnly}>
      {user=><AppShell user={user}>
        <div className="mx-auto max-w-2xl">{children(user)}</div>
      </AppShell>}
    </AuthGate>
  );
}
export function MtHeader({title,back="/mt"}:{title:string;back?:string}) {
  return <header className="mb-5 flex items-center gap-3">
    <Link href={back} aria-label="뒤로가기" className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl border border-slate-200 bg-white text-xl text-slate-700">‹</Link>
    <h1 className="text-xl font-extrabold text-slate-950">{title}</h1>
  </header>;
}
export function MtLocked() {
  return <div className="rounded-3xl border border-slate-200 bg-white px-4 py-16 text-center text-sm font-medium text-slate-400">잠겨있는 기능입니다</div>;
}
export function MtCard({children,className=""}:{children:React.ReactNode;className?:string}) {
  return <section className={`rounded-3xl border border-slate-200 bg-white p-5 shadow-sm ${className}`}>{children}</section>;
}
export function MtError({message}:{message:string}) {
  return <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{message}</p>;
}
export function MtSwitch({label,checked,onChange,disabled=false}:{label:string;checked:boolean;onChange:(checked:boolean)=>void;disabled?:boolean}) {
  return <button type="button" role="switch" aria-label={label} aria-checked={checked} disabled={disabled}
    onClick={()=>onChange(!checked)} className={`relative h-8 w-14 rounded-full p-1 transition-colors disabled:opacity-50 ${checked?"bg-[#34C759]":"bg-slate-300"}`}>
    <span className={`block h-6 w-6 rounded-full bg-white shadow transition-transform ${checked?"translate-x-6":"translate-x-0"}`}/>
  </button>;
}
