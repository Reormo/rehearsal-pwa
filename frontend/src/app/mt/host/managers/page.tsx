"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtHeader, MtCard, MtError } from "@/components/mt-layout";
import { AuthUser, errorMessage } from "@/lib/api";
import { mtApi, MtManager } from "@/lib/mt-api";

export default function MtManagersPage() {
  return <MtLayout hostOnly>{user=><ManagerContent user={user}/>}</MtLayout>;
}
function ManagerContent({user}:{user:AuthUser}) {
  const superAdmin=user.role==="SUPER_ADMIN";
  const client=useQueryClient();
  const managers=useQuery({queryKey:["mt","managers"],queryFn:mtApi.managers,enabled:superAdmin});
  const [search,setSearch]=useState("");
  const [busy,setBusy]=useState<number|null>(null);
  const [error,setError]=useState("");
  async function toggle(m:MtManager) {
    if(m.permanent||busy!==null)return;
    if(m.assigned&&!window.confirm(`${m.name}님의 MT 관리자 권한을 해제할까요?`))return;
    setBusy(m.id);setError("");
    try {
      if(m.assigned)await mtApi.revokeManager(m.id);
      else await mtApi.grantManager(m.id);
      await client.invalidateQueries({queryKey:["mt"]});
    } catch(e){setError(errorMessage(e));}
    finally{setBusy(null);}
  }
  const shown=managers.data?.filter(m=>m.name.toLowerCase().includes(search.toLowerCase()))??[];
  return <>
    <MtHeader title="MT 관리자 지정" back="/mt/host"/>
    {!superAdmin?<MtError message="슈퍼관리자만 MT 관리자를 지정할 수 있습니다."/>:<>
      <p className="mb-4 text-sm text-slate-600">MT 관리자는 게임과 조·문제를 운영하지만, 합주 예약 관리자 권한은 갖지 않습니다.</p>
      {error&&<div className="mb-3"><MtError message={error}/></div>}
      <MtCard>
        <label htmlFor="mt-manager-search" className="mb-2 block text-sm font-semibold text-slate-700">회원 검색</label>
        <input id="mt-manager-search" value={search} onChange={e=>setSearch(e.target.value)} placeholder="회원 이름 검색"
          className="mb-3 w-full rounded-xl border border-slate-200 px-3 py-3 text-base"/>
        {managers.isPending?<p className="text-sm text-slate-500">회원 목록 불러오는 중...</p>:
          managers.isError?<div className="space-y-2"><MtError message={errorMessage(managers.error)}/>
            <button type="button" onClick={()=>void managers.refetch()} className="text-sm font-semibold text-blue-600">다시 시도</button></div>:
          !shown.length?<p className="py-5 text-center text-sm text-slate-500">검색 결과가 없습니다.</p>:
          <div className="divide-y divide-slate-100">{shown.map(m=>
            <div key={m.id} className="flex items-center justify-between gap-3 py-3">
              <div className="min-w-0">
                <p className="truncate font-semibold text-slate-900">{m.name}</p>
                <p className="text-xs text-slate-500">{m.permanent?"슈퍼관리자 · 기본 MC":m.assigned?"MT 관리자":m.role==="ADMIN"?"합주 관리자":"일반 회원"}</p>
              </div>
              {m.permanent?<span className="shrink-0 text-xs font-semibold text-slate-500">고정</span>:
                <button type="button" disabled={busy!==null} onClick={()=>void toggle(m)}
                  aria-label={`${m.name} MT 관리자 ${m.assigned?"해제":"지정"}`}
                  className={`shrink-0 rounded-xl border px-4 py-2 text-sm font-bold disabled:opacity-50 ${m.assigned?"border-red-200 text-red-600":"border-blue-200 bg-blue-50 text-blue-700"}`}>
                  {busy===m.id?"처리 중":m.assigned?"권한 해제":"관리자 지정"}
                </button>}
            </div>)}</div>}
      </MtCard>
    </>}
  </>;
}
