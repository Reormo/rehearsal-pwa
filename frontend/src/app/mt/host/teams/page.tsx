"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtHeader, MtCard, MtError } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

export default function MtTeamsPage() {
  const client=useQueryClient();
  const teams=useQuery({queryKey:["mt","teams"],queryFn:mtApi.teams});
  const members=useQuery({queryKey:["mt","members"],queryFn:mtApi.members});
  const [teamId,setTeamId]=useState<number|null>(null);
  const [newName,setNewName]=useState("");
  const [rename,setRename]=useState("");
  const [search,setSearch]=useState("");
  const [memberId,setMemberId]=useState("");
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const team=teams.data?.find(t=>t.id===teamId)||teams.data?.[0];
  useEffect(()=>{if(team)setRename(team.name)},[team?.id,team?.name]);

  async function run(fn:()=>Promise<unknown>) {
    setBusy(true);setError("");
    try{await fn();await client.invalidateQueries({queryKey:["mt"]});}
    catch(e){setError(errorMessage(e));}
    finally{setBusy(false);}
  }
  const membersWithLocation=members.data?.map(member=>{
    const current=teams.data?.find(t=>t.members.some(m=>m.id===member.id));
    return {...member,currentTeam:current?.name??""};
  }).filter(m=>m.name.toLowerCase().includes(search.toLowerCase())&&!team?.members.some(t=>t.id===m.id))??[];

  return <MtLayout hostOnly>{()=> <>
    <MtHeader title="조 관리" back="/mt/host"/>
    {error&&<div className="mb-3"><MtError message={error}/></div>}
    <MtCard className="mb-3">
      <h2 className="mb-3 font-bold text-slate-900">조 추가</h2>
      <div className="flex gap-2">
        <input value={newName} maxLength={80} onChange={e=>setNewName(e.target.value)} placeholder="새 조 이름"
          className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3 py-2 text-base"/>
        <button disabled={busy||!newName.trim()} onClick={()=>run(async()=>{await mtApi.addTeam(newName);setNewName("")})}
          className="rounded-xl bg-slate-900 px-4 font-bold text-white disabled:opacity-50">추가</button>
      </div>
    </MtCard>
    <MtCard className="mb-3">
      <h2 className="mb-3 font-bold text-slate-900">조 목록</h2>
      {!teams.data?.length&&<p className="text-sm text-slate-400">등록된 조가 없습니다.</p>}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {teams.data?.map(t=><button key={t.id} onClick={()=>{setTeamId(t.id);setMemberId("");setSearch("")}}
          className={`rounded-xl border px-3 py-3 text-left ${team?.id===t.id?"border-blue-400 bg-blue-50":"border-slate-200 bg-white"}`}>
          <p className="truncate font-bold text-slate-900">{t.name}</p>
          <p className="mt-1 text-xs text-slate-500">{t.members.length}명</p>
        </button>)}
      </div>
    </MtCard>
    {team&&<MtCard className="space-y-5">
      <div>
        <h2 className="mb-3 font-bold text-slate-900">{team.name} · 이름 수정</h2>
        <div className="flex gap-2">
          <input aria-label="조 이름 변경" value={rename} maxLength={80} onChange={e=>setRename(e.target.value)}
            className="min-w-0 flex-1 rounded-xl border border-slate-200 px-3 py-2 text-base"/>
          <button disabled={busy||!rename.trim()||rename===team.name} onClick={()=>run(()=>mtApi.renameTeam(team.id,rename))}
            className="rounded-xl border border-slate-200 px-4 font-semibold text-slate-800 disabled:opacity-50">저장</button>
        </div>
      </div>
      <div className="border-t border-slate-100 pt-4">
        <h3 className="mb-3 font-bold text-slate-900">배정된 조원 · {team.members.length}명</h3>
        {team.members.length===0&&<p className="text-sm text-slate-400">배정된 조원이 없습니다.</p>}
        <div className="divide-y divide-slate-100">
          {team.members.map(m=><div key={m.id} className="flex items-center justify-between gap-2 py-2.5">
            <span className="text-sm font-semibold text-slate-800">{m.name}</span>
            <button disabled={busy} onClick={()=>run(()=>mtApi.unassign(team.id,m.id))}
              className="rounded-lg border border-slate-200 px-3 py-1.5 text-xs font-semibold text-slate-600 disabled:opacity-50">조 제외</button>
          </div>)}
        </div>
      </div>
      <div className="border-t border-slate-100 pt-4">
        <h3 className="mb-3 font-bold text-slate-900">조원 추가 / 이동</h3>
        <input value={search} onChange={e=>setSearch(e.target.value)} placeholder="회원 이름 검색"
          className="mb-2 w-full rounded-xl border border-slate-200 px-3 py-2.5 text-base"/>
        <select aria-label="회원 선택" value={memberId} onChange={e=>setMemberId(e.target.value)}
          className="mb-2 w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm text-slate-800">
          <option value="">회원을 선택하세요</option>
          {membersWithLocation.map(m=><option key={m.id} value={m.id}>{m.name}{m.currentTeam?` · ${m.currentTeam}`:""}</option>)}
        </select>
        <button disabled={!memberId||busy} onClick={()=>{
          const member=membersWithLocation.find(m=>m.id===Number(memberId));
          if(!member)return;
          if(member.currentTeam&&!window.confirm(`${member.name}님을 ${member.currentTeam}에서 ${team.name}(으)로 이동할까요?`))return;
          void run(async()=>{await mtApi.assign(team.id,member.id);setMemberId("");setSearch("")});
        }} className="w-full rounded-xl bg-slate-900 p-3 font-bold text-white disabled:opacity-50">이 조에 배정</button>
      </div>
      <div className="border-t border-slate-100 pt-4">
        <button disabled={busy} onClick={()=>{
          if(!window.confirm(`${team.name}를 삭제할까요? 배정된 조원은 조에서 제외되며 이전 게임 기록은 유지됩니다.`))return;
          void run(async()=>{await mtApi.deleteTeam(team.id);setTeamId(null)});
        }} className="rounded-xl border border-red-200 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-50">조 삭제</button>
      </div>
    </MtCard>}
    <p className="mt-3 text-xs text-slate-400">기존 합주팀 정보와는 별개로 관리됩니다.</p>
    <Link href="/mt/host" className="mt-4 inline-block text-sm font-semibold text-slate-600">MC 화면으로 돌아가기</Link>
  </>}</MtLayout>;
}
