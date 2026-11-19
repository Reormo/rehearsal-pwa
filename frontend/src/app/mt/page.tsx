"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useQuery } from "@tanstack/react-query";
import { MtLayout, MtGameCard, MtLoading } from "@/components/mt-layout";
import { mtApi } from "@/lib/mt-api";

export default function MtPage() {
  const router=useRouter();
  const state=useQuery({queryKey:["mt","state"],queryFn:mtApi.state,refetchInterval:2500});
  useEffect(()=>{if(state.data?.host)router.replace("/mt/host")},[router,state.data?.host]);
  return <MtLayout>{()=> <>
    {state.isPending?<MtLoading/>:state.isError?
      <p className="text-sm text-red-600">MT 페이지를 불러오지 못했어요.</p>:state.data?.host?
      <p className="text-sm text-slate-500">MT 관리자 화면으로 이동 중...</p>:<>
      {state.data?.exists && state.data.team && <p className="mb-4 inline-flex rounded-full bg-white/85 px-3 py-1.5 text-sm font-bold text-[#AE4051]">{state.data.team.name}</p>}
      <div className="grid gap-4">
        <MtGameCard href="/mt/buzzer" title="버저비터" kind="buzzer" visible={Boolean(state.data?.buzzerVisible)}/>
        <MtGameCard href="/mt/typing" title="문자 빨리 보내!" kind="typing" visible={Boolean(state.data?.typingVisible)}/>
      </div>
    </>}
  </>}</MtLayout>;
}
