"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useQuery } from "@tanstack/react-query";
import { AuthGate } from "@/components/auth-gate";
import { AppShell } from "@/components/app-shell";
import { AuthUser, errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

export function MtLayout({children,hostOnly=false}:{children:(user:AuthUser)=>React.ReactNode;hostOnly?:boolean}) {
  const pathname=usePathname();
  return (
    <AuthGate>
      {user=><AppShell user={user}>
        <div className={`mx-auto max-w-2xl ${pathname === "/mt/typing" ? "mt-typing-layout" : ""}`}>
          {hostOnly?<MtHostAccess>{children(user)}</MtHostAccess>:children(user)}
        </div>
      </AppShell>}
    </AuthGate>
  );
}
function MtHostAccess({children}:{children:React.ReactNode}) {
  const state=useQuery({queryKey:["mt","state"],queryFn:mtApi.state,refetchInterval:3000});
  if(state.isPending)return <MtLoading/>;
  if(state.isError)return <div className="space-y-3">
    <MtError message={errorMessage(state.error)}/>
    <button type="button" onClick={()=>void state.refetch()} className="rounded-xl border border-slate-200 px-4 py-2 text-sm">다시 시도</button>
  </div>;
  if(!state.data.host)return <div className="rounded-2xl bg-white p-5 text-center">
    <p className="text-sm text-slate-600">MT 관리자 권한이 없습니다.</p>
    <Link href="/mt" className="mt-3 inline-block text-sm font-semibold text-blue-600">MT 참가자 화면</Link>
  </div>;
  return <>{children}</>;
}
export function MtGameCard({href,title,kind,visible}:{href:string;title:string;kind:"buzzer"|"typing";visible:boolean}) {
  if(!visible) return <div className="mt-game-tile mt-game-tile-locked" aria-label="잠겨 있는 기능">
    <span className="mt-game-glyph" aria-hidden="true">🔒</span>
    <strong className="mt-game-label">잠겨 있는 기능</strong>
  </div>;
  return <Link href={href} className={`mt-game-tile mt-game-tile-${kind}`} aria-label={title}>
    <span className="mt-game-glyph" aria-hidden="true">{kind==="buzzer"?"🎯":"💬"}</span>
    <strong className="mt-game-label">{title}</strong>
  </Link>;
}
export function MtLoading() {
  return <div className="mt-loading" role="status" aria-label="MT 정보를 불러오는 중">
    <span className="mt-loading-ring" aria-hidden="true"/>
  </div>;
}
export function MtLocked() {
  return <div className="mt-locked-panel rounded-3xl px-4 py-16 text-center text-sm font-semibold text-slate-400">잠겨있는 기능입니다</div>;
}
export function MtCard({children,className=""}:{children:React.ReactNode;className?:string}) {
  return <section className={`mt-card rounded-3xl border p-5 ${className}`}>{children}</section>;
}
export function MtError({message}:{message:string}) {
  return <p role="alert" className="rounded-xl bg-red-50 p-3 text-sm text-red-700">{message}</p>;
}
export function MtSwitch({label,checked,onChange,disabled=false}:{label:string;checked:boolean;onChange:(checked:boolean)=>void;disabled?:boolean}) {
  return <button type="button" role="switch" aria-label={label} aria-checked={checked} disabled={disabled}
    onClick={()=>onChange(!checked)} className={`relative h-8 w-14 rounded-full p-1 transition-colors disabled:opacity-50 ${checked?"bg-[#EC5A6B]":"bg-slate-300"}`}>
    <span className={`block h-6 w-6 rounded-full bg-white shadow transition-transform ${checked?"translate-x-6":"translate-x-0"}`}/>
  </button>;
}
