"use client";

import Link from "next/link";
import { useQuery } from "@tanstack/react-query";
import { AuthGate } from "@/components/auth-gate";
import { AppShell } from "@/components/app-shell";
import { AuthUser, errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

export function MtLayout({children,hostOnly=false}:{children:(user:AuthUser)=>React.ReactNode;hostOnly?:boolean}) {
  return (
    <AuthGate>
      {user=><AppShell user={user}>
        <div className="mx-auto max-w-2xl">
          {hostOnly?<MtHostAccess>{children(user)}</MtHostAccess>:children(user)}
        </div>
      </AppShell>}
    </AuthGate>
  );
}
function MtHostAccess({children}:{children:React.ReactNode}) {
  const state=useQuery({queryKey:["mt","state"],queryFn:mtApi.state,refetchInterval:3000});
  if(state.isPending)return <p className="py-8 text-center text-sm text-slate-500">MT 관리자 권한을 확인하는 중...</p>;
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
