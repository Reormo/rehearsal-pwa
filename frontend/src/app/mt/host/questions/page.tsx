"use client";

import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtHeader, MtCard, MtError } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

export default function MtQuestionsPage() {
  const client=useQueryClient();
  const questions=useQuery({queryKey:["mt","questions"],queryFn:mtApi.questions});
  const [selectedId,setSelectedId]=useState<number|null>(null);
  const [editDrafts,setEditDrafts]=useState<Record<number,string>>({});
  const [newText,setNewText]=useState("");
  const [busy,setBusy]=useState(false);
  const [error,setError]=useState("");
  const selected=questions.data?.find(q=>q.id===selectedId)??questions.data?.[0];
  const editing=selected?(editDrafts[selected.id]??selected.text):"";

  async function run(fn:()=>Promise<unknown>) {
    setBusy(true);setError("");
    try{await fn();await client.invalidateQueries({queryKey:["mt"]});}
    catch(e){setError(errorMessage(e))}
    finally{setBusy(false);}
  }
  return <MtLayout hostOnly>{()=> <>
    <MtHeader title="문장 관리" back="/mt/host"/>
    {error&&<div className="mb-3"><MtError message={error}/></div>}
    <MtCard className="mb-3">
      <h2 className="mb-3 font-bold text-slate-900">문제 추가</h2>
      <textarea value={newText} maxLength={500} onChange={e=>setNewText(e.target.value)}
        placeholder="새 문장을 입력하세요. 이모지도 사용할 수 있어요."
        rows={3} className="w-full rounded-xl border border-slate-200 p-3 text-base"/>
      <button disabled={busy||!newText.trim()} onClick={()=>run(async()=>{await mtApi.addQuestion(newText);setNewText("")})}
        className="mt-2 w-full rounded-xl bg-slate-900 p-3 font-bold text-white disabled:opacity-50">문제 추가</button>
    </MtCard>
    <MtCard className="mb-3">
      <h2 className="mb-3 font-bold text-slate-900">등록된 문제 · {questions.data?.length??0}개</h2>
      <div className="divide-y divide-slate-100">
        {questions.data?.map((q,i)=><div key={q.id} className="flex items-center gap-2 py-2">
          <button onClick={()=>setSelectedId(q.id)} className={`min-w-0 flex-1 rounded-lg px-3 py-2 text-left text-sm ${selected?.id===q.id?"bg-blue-50 font-semibold text-blue-800":"text-slate-800"}`}>
            <span className="mr-2 text-slate-400">{i+1}.</span>{q.text}
          </button>
          <div className="flex shrink-0 gap-1">
            <button type="button" aria-label={`${i+1}번 위로 이동`} disabled={busy||i===0}
              onClick={()=>run(()=>mtApi.moveQuestion(q.id,"UP"))}
              className="rounded-lg border border-slate-200 px-2 py-1.5 text-slate-600 disabled:opacity-25">↑</button>
            <button type="button" aria-label={`${i+1}번 아래로 이동`} disabled={busy||i===(questions.data?.length??0)-1}
              onClick={()=>run(()=>mtApi.moveQuestion(q.id,"DOWN"))}
              className="rounded-lg border border-slate-200 px-2 py-1.5 text-slate-600 disabled:opacity-25">↓</button>
          </div>
        </div>)}
      </div>
    </MtCard>
    {selected&&<MtCard>
      <h2 className="mb-3 font-bold text-slate-900">문장 편집</h2>
      <textarea aria-label="기준 문장" value={editing} maxLength={500} rows={5} onChange={e=>setEditDrafts(p=>({...p,[selected.id]:e.target.value}))}
        className="w-full rounded-xl border border-slate-200 p-3 text-base text-slate-900"/>
      <div className="mt-3 flex items-center justify-between gap-2">
        <button disabled={busy} onClick={()=>{
          if(window.confirm("이 문제를 삭제할까요? 이전 제출 기록은 유지됩니다."))
            void run(async()=>{await mtApi.deleteQuestion(selected.id);setSelectedId(null)});
        }} className="rounded-xl border border-red-200 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-50">삭제</button>
        <button disabled={busy||!editing.trim()||editing===selected.text}
          onClick={()=>run(()=>mtApi.editQuestion(selected.id,editing))}
          className="rounded-xl bg-slate-900 px-5 py-2.5 font-bold text-white disabled:opacity-50">저장</button>
      </div>
    </MtCard>}
  </>}</MtLayout>;
}
