"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtHeader, MtCard, MtError, MtSwitch } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

type Tab="settings"|"buzzer"|"typing";
export default function MtHostPage() {
  const client=useQueryClient();
  const host=useQuery({queryKey:["mt","host"],queryFn:mtApi.host,refetchInterval:1000});
  const teams=useQuery({queryKey:["mt","teams"],queryFn:mtApi.teams,refetchInterval:3000});
  const questions=useQuery({queryKey:["mt","questions"],queryFn:mtApi.questions,refetchInterval:3000});
  const results=useQuery({queryKey:["mt","results"],queryFn:mtApi.results,refetchInterval:1200,enabled:Boolean(host.data?.currentRoundId)});
  const [tab,setTab]=useState<Tab>("settings");
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const [newEvent,setNewEvent]=useState("무혼 MT");
  const [selectedQuestion,setSelectedQuestion]=useState<number|null>(null);

  useEffect(()=>{
    if(questions.data?.length && !questions.data.some(q=>q.id===selectedQuestion))
      setSelectedQuestion(questions.data[0].id);
  },[questions.data,selectedQuestion]);

  async function run(task:()=>Promise<unknown>){
    if(busy)return;
    setBusy(true);setError("");
    try {await task();await client.invalidateQueries({queryKey:["mt"]});}
    catch(e){setError(errorMessage(e));}
    finally{setBusy(false);}
  }
  useEffect(()=>{
    if(host.data?.typingOpen && results.data?.questionId)
      setSelectedQuestion(results.data.questionId);
  },[host.data?.typingOpen,results.data?.questionId]);
  const selected=questions.data?.find(q=>q.id===selectedQuestion);
  const visibleResults=results.data?.questionId===selectedQuestion?results.data:null;
  const excluded=new Set(host.data?.excludedTeamIds??[]);
  return <MtLayout hostOnly>{()=> <>
    <MtHeader title="MT 관리"/>
    {error&&<div className="mb-3"><MtError message={error}/></div>}
    {host.isPending?<p className="text-sm text-slate-500">불러오는 중...</p>:host.isError?
      <MtError message={errorMessage(host.error)}/>:
    !host.data?.exists?
      <MtCard>
        <h2 className="mb-4 text-lg font-bold text-slate-900">MT 행사 만들기</h2>
        <label htmlFor="event-title" className="text-sm font-semibold text-slate-600">행사 이름</label>
        <input id="event-title" value={newEvent} maxLength={120} onChange={e=>setNewEvent(e.target.value)} className="mt-2 w-full rounded-xl border border-slate-200 px-3 py-3"/>
        <button disabled={busy||!newEvent.trim()} onClick={()=>run(()=>mtApi.createEvent(newEvent))} className="mt-4 w-full rounded-xl bg-slate-900 p-3 font-bold text-white disabled:opacity-50">생성하기</button>
      </MtCard>:
      <>
        <div className="mb-4 flex flex-wrap gap-2">
          {([{id:"settings",label:"설정"},{id:"buzzer",label:"버저 진행"},{id:"typing",label:"문자 게임 진행"}] as const).map(item=>
            <button type="button" key={item.id} onClick={()=>setTab(item.id)}
              className={`rounded-xl border px-3 py-2 text-sm font-semibold ${tab===item.id?"border-slate-900 bg-slate-900 text-white":"border-slate-200 bg-white text-slate-600"}`}>{item.label}</button>)}
        </div>
        {tab==="settings"&&<div className="space-y-3">
          <MtCard>
            <h2 className="mb-4 font-bold text-slate-900">기능 공개</h2>
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-slate-800">버저비터</span>
              <MtSwitch label="버저비터 공개" disabled={busy} checked={Boolean(host.data.buzzerVisible)}
                onChange={enabled=>run(()=>mtApi.visibility("BUZZER",enabled))}/>
            </div>
            <div className="my-4 border-t border-slate-100"/>
            <div className="flex items-center justify-between gap-3">
              <span className="font-semibold text-slate-800">문자 빨리 보내!</span>
              <MtSwitch label="문자 빨리 보내 공개" disabled={busy} checked={Boolean(host.data.typingVisible)}
                onChange={enabled=>run(()=>mtApi.visibility("TYPING",enabled))}/>
            </div>
          </MtCard>
          <MtCard className="space-y-3">
            <h2 className="font-bold text-slate-900">운영 메뉴</h2>
            <Link href="/mt/host/teams" className="block rounded-xl border border-slate-200 p-3 font-semibold text-slate-800">조 관리 →</Link>
            <Link href="/mt/host/questions" className="block rounded-xl border border-slate-200 p-3 font-semibold text-slate-800">문장 관리 →</Link>
          </MtCard>
          <MtCard>
            <p className="mb-2 text-sm text-slate-500">{host.data.eventTitle}</p>
            <button disabled={busy||Boolean(host.data.buzzerOpen)||Boolean(host.data.typingOpen)}
              onClick={()=>{if(window.confirm("현재 MT 행사를 종료할까요? 기록은 보관되고 새 행사를 만들 수 있어요."))void run(mtApi.endEvent)}}
              className="rounded-xl border border-red-200 px-4 py-2 text-sm font-bold text-red-600 disabled:opacity-50">행사 종료</button>
          </MtCard>
        </div>}
        {tab==="buzzer"&&<div className="space-y-3">
          <MtCard>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-bold text-slate-900">버저 진행</h2>
              <span className="text-sm text-slate-500">{host.data.buzzerOpen?"진행 중":host.data.winnerTeamId?"접수 완료":"대기"}</span>
            </div>
            <div className="mb-4 rounded-2xl bg-slate-50 py-10 text-center">
              <p className="mb-1 text-sm text-slate-500">선착순</p>
              <p aria-live="polite" className="text-4xl font-extrabold text-blue-600">{host.data.winnerTeamName??"—"}</p>
              {excluded.size>0&&<p className="mt-3 px-3 text-xs text-slate-500">제외: {teams.data?.filter(t=>excluded.has(t.id)).map(t=>t.name).join(", ")}</p>}
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <button disabled={busy||!host.data.buzzerVisible||Boolean(host.data.buzzerOpen)||Boolean(host.data.winnerTeamId)}
                className="rounded-xl bg-blue-600 p-3 font-bold text-white disabled:opacity-40" onClick={()=>run(()=>mtApi.buzzerAction("OPEN"))}>버저 열기</button>
              <button disabled={busy||!host.data.buzzerVisible||(!host.data.buzzerOpen&&!host.data.winnerTeamId)}
                className="rounded-xl border border-slate-200 p-3 font-bold text-slate-700 disabled:opacity-40" onClick={()=>run(()=>mtApi.buzzerAction("CLOSE"))}>버저 닫기</button>
              <button disabled={busy||!host.data.buzzerVisible||!host.data.winnerTeamId}
                className="rounded-xl border border-slate-200 p-3 font-bold text-slate-700 disabled:opacity-40" onClick={()=>run(()=>mtApi.buzzerAction("OTHER"))}>다른 조에 기회</button>
              <button disabled={busy||!host.data.buzzerVisible||excluded.size===0}
                className="rounded-xl border border-slate-200 p-3 font-bold text-slate-700 disabled:opacity-40" onClick={()=>run(()=>mtApi.buzzerAction("RETRY"))}>재도전 허용</button>
            </div>
            <button disabled={busy||!host.data.buzzerVisible}
              className="mt-4 w-full rounded-xl bg-slate-100 p-3 font-bold text-slate-700 disabled:opacity-40"
              onClick={()=>run(()=>mtApi.buzzerAction("RESET"))}>초기화</button>
          </MtCard>
        </div>}
        {tab==="typing"&&<div className="space-y-3">
          <MtCard>
            <h2 className="mb-3 font-bold text-slate-900">문자 게임 진행</h2>
            <div className="mb-3 flex items-center justify-between">
              <span className="text-sm text-slate-500">기준 문장</span>
              <Link href="/mt/host/questions" className="text-sm font-semibold text-blue-600">문장 관리</Link>
            </div>
            <select aria-label="문제 선택" value={selectedQuestion??""}
              disabled={busy||Boolean(host.data.typingOpen)}
              onChange={e=>setSelectedQuestion(Number(e.target.value))}
              className="w-full rounded-xl border border-slate-200 bg-white p-3 text-sm">
              {questions.data?.map((q,i)=><option key={q.id} value={q.id}>{i+1}번 문제</option>)}
            </select>
            <div className="my-3 whitespace-pre-wrap break-words rounded-xl bg-slate-50 p-3 text-sm text-slate-800">{selected?.text??"등록된 문장이 없습니다."}</div>
            <p className="mb-3 text-sm font-semibold text-slate-500">{host.data.typingOpen?"접수 중":"접수 대기 / 종료"}</p>
            <div className="grid grid-cols-2 gap-2">
              <button className="rounded-xl bg-slate-900 p-3 font-bold text-white disabled:opacity-40"
                disabled={busy||!selectedQuestion||!host.data.typingVisible||Boolean(host.data.typingOpen)}
                onClick={()=>run(()=>mtApi.startTyping(selectedQuestion!))}>접수 시작</button>
              <button className="rounded-xl border border-slate-200 p-3 font-bold text-slate-700 disabled:opacity-40"
                disabled={busy||!host.data.typingOpen}
                onClick={()=>run(mtApi.closeTyping)}>접수 종료</button>
            </div>
          </MtCard>
          <MtCard>
            <h2 className="mb-3 font-bold text-slate-900">{host.data.typingOpen?"실시간 순위":"최종 순위"}</h2>
            {!visibleResults?.ranking.length?<p className="py-5 text-center text-sm text-slate-400">정확한 제출이 아직 없습니다.</p>:
              <div className="divide-y divide-slate-100">
                {visibleResults!.ranking.map((r,i)=><div key={i} className="flex items-center gap-3 py-3 text-sm">
                  <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-100 font-bold">{i+1}</span>
                  <span className="font-semibold text-slate-900">{r.name}</span>
                  <span className="ml-auto text-slate-500">{r.teamName}</span>
                </div>)}
              </div>}
          </MtCard>
          <MtCard>
            <div className="mb-3 flex items-center justify-between">
              <h2 className="font-bold text-slate-900">전체 제출 현황</h2>
              <span className="text-xs text-slate-500">{visibleResults?.submissions.length??0}건</span>
            </div>
            {!visibleResults?.submissions.length?<p className="py-5 text-center text-sm text-slate-400">제출 내역이 없습니다.</p>:
              <div className="max-h-[450px] space-y-3 overflow-y-auto">
                {[...visibleResults!.submissions].reverse().map(s=><article key={s.id} className="border-b border-slate-100 pb-3">
                  <div className="mb-1 flex flex-wrap items-center gap-2 text-xs">
                    <strong className="text-sm text-slate-900">{s.user_name}</strong>
                    <span className="text-slate-500">{s.team_name}</span>
                    <span className={`ml-auto font-semibold ${s.correct?"text-green-600":"text-red-500"}`}>{s.correct?"정답":"오답"}</span>
                    <span className="text-slate-400">{s.latest?"최신":"이전"}</span>
                  </div>
                  <p className="whitespace-pre-wrap break-words text-sm text-slate-700">{s.content}</p>
                </article>)}
              </div>}
          </MtCard>
        </div>}
      </>}
  </>}</MtLayout>;
}
