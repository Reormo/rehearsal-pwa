"use client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtCard, MtError } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

function formatDate(value:string) {
  return new Date(value).toLocaleDateString("ko-KR",{year:"numeric",month:"long",day:"numeric"});
}
export default function MtHistoryPage() {
  const router=useRouter();
  const client=useQueryClient();
  const history=useQuery({queryKey:["mt","history"],queryFn:mtApi.history});
  const [selected,setSelected]=useState<number|null>(null);
  const detail=useQuery({queryKey:["mt","history",selected],queryFn:()=>mtApi.historyDetail(selected!),enabled:selected!==null});
  const [busy,setBusy]=useState<number|null>(null);
  const [error,setError]=useState("");

  async function restore(id:number,title:string) {
    if(busy!==null||!window.confirm(`‘${title}’ 행사를 다시 진행할까요? 기존 조·문제·기록이 유지됩니다.`))return;
    setBusy(id);setError("");
    try {
      await mtApi.restoreEvent(id);
      await client.invalidateQueries({queryKey:["mt"]});
      router.replace("/mt/host");
    }catch(e){setError(errorMessage(e))}
    finally{setBusy(null)}
  }
  async function remove(id:number,title:string) {
    if(busy!==null)return;
    const answer=window.prompt(`‘${title}’ 행사와 조·문제·제출 기록을 영구 삭제합니다. 복구할 수 없습니다. 확인하려면 행사 이름을 입력하세요.`);
    if(answer!==title)return;
    setBusy(id);setError("");
    try {
      await mtApi.deleteArchivedEvent(id);
      if(selected===id)setSelected(null);
      await client.invalidateQueries({queryKey:["mt","history"]});
    }catch(e){setError(errorMessage(e))}
    finally{setBusy(null)}
  }

  return <MtLayout hostOnly>{()=> <div className="space-y-3">
    {error&&<MtError message={error}/>}
    {history.isPending?<p className="text-sm text-slate-500">불러오는 중...</p>:
      history.isError?<MtError message={errorMessage(history.error)}/>:
      history.data?.length===0?<MtCard><p className="py-8 text-center text-sm text-slate-500">지난 MT 기록이 없습니다.</p></MtCard>:
      history.data?.map(item=><MtCard key={item.id}>
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <p className="truncate font-bold text-slate-900">{item.title}</p>
            <p className="mt-1 text-xs text-slate-500">{formatDate(item.ended_at)}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <button type="button" onClick={()=>setSelected(s=>s===item.id?null:item.id)}
              className="mt-outline-button rounded-xl px-3 py-2 text-xs font-bold">{selected===item.id?"접기":"기록 보기"}</button>
            <button type="button" disabled={busy!==null} onClick={()=>void restore(item.id,item.title)}
              className="mt-primary-button rounded-xl px-3 py-2 text-xs font-bold text-white">복구</button>
            <button type="button" disabled={busy!==null} onClick={()=>void remove(item.id,item.title)}
              className="mt-danger-button rounded-xl border px-3 py-2 text-xs font-bold">삭제</button>
          </div>
        </div>
        <div className="mt-3 flex gap-3 text-xs text-slate-500">
          <span>조 {item.team_count}개</span><span>문제 {item.question_count}개</span>
          <span>제출 {item.submission_count}건</span>
        </div>
        {selected===item.id&&<div className="mt-4 border-t border-rose-100 pt-4">
          {detail.isPending?<p className="text-sm text-slate-400">기록 불러오는 중...</p>:
            detail.isError?<MtError message={errorMessage(detail.error)}/>:
            <div className="space-y-4 text-sm">
              <section><h3 className="mb-2 font-bold">조</h3>
                <div className="flex flex-wrap gap-2">{detail.data?.teams.map(t=><span key={t.id} className="rounded-lg bg-rose-50 px-3 py-1.5 text-xs">{t.name} · {t.member_count}명</span>)}</div>
              </section>
              <section><h3 className="mb-2 font-bold">문제</h3>
                <div className="space-y-2">{detail.data?.questions.map((q,i)=><p key={q.id} className="whitespace-pre-wrap break-words rounded-lg bg-slate-50 p-2 text-xs">{i+1}. {q.text}</p>)}</div>
              </section>
              <section><h3 className="mb-2 font-bold">문자 게임 제출 · {detail.data?.submissions.length??0}건</h3>
                {!detail.data?.submissions.length?<p className="text-xs text-slate-400">제출 기록이 없습니다.</p>:
                  <div className="max-h-64 space-y-2 overflow-y-auto">
                    {detail.data.submissions.map(s=><div key={s.id} className="rounded-lg bg-slate-50 p-2">
                      <p className="mb-1 text-xs font-bold">{s.user_name} · {s.team_name}</p>
                      <p className="whitespace-pre-wrap break-words text-xs">{s.content}</p>
                    </div>)}
                  </div>}
              </section>
            </div>}
        </div>}
      </MtCard>)}
  </div>}</MtLayout>;
}
