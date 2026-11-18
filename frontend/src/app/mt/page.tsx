"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { MtLayout, MtHeader, MtLocked } from "@/components/mt-layout";
import { mtApi } from "@/lib/mt-api";

export default function MtPage() {
  const router=useRouter();
  const state=useQuery({queryKey:["mt","state"],queryFn:mtApi.state,refetchInterval:2500});
  useEffect(()=>{if(state.data?.host)router.replace("/mt/host")},[router,state.data?.host]);
  return <MtLayout>{()=> <>
    <MtHeader title="MT" back="/my"/>
    {state.isPending?<p className="text-sm text-slate-500">불러오는 중...</p>:state.isError?
      <p className="text-sm text-red-600">MT 페이지를 불러오지 못했어요.</p>:state.data?.host?
      <p className="text-sm text-slate-500">MT 관리자 화면으로 이동 중...</p>:<>
      {state.data?.exists && state.data.team && <p className="mb-4 text-sm font-semibold text-slate-500">{state.data.team.name}</p>}
      <div className="space-y-3">
        {state.data?.buzzerVisible?<Link href="/mt/buzzer" className="block rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-lg font-bold text-slate-950">버저비터</p><p className="mt-1 text-sm text-slate-500">버저 누르기</p></Link>:<MtLocked/>}
        {state.data?.typingVisible?<Link href="/mt/typing" className="block rounded-3xl border border-slate-200 bg-white p-5 shadow-sm">
          <p className="text-lg font-bold text-slate-950">문자 빨리 보내!</p><p className="mt-1 text-sm text-slate-500">문장 입력하기</p></Link>:<MtLocked/>}
      </div>
    </>}
  </>}</MtLayout>;
}
