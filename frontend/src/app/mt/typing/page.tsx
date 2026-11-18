"use client";

import { useEffect, useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtLocked, MtError } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

export default function MtTypingPage() {
  const client=useQueryClient();
  const status=useQuery({queryKey:["mt","typing"],queryFn:mtApi.typing,refetchInterval:1100});
  const [draft,setDraft]=useState<{roundId:number|null;text:string}>({roundId:null,text:""});
  const [sending,setSending]=useState(false);
  const [error,setError]=useState("");
  const scrollArea=useRef<HTMLDivElement>(null);
  useEffect(()=>{
    const viewport=window.visualViewport;
    const update=()=>document.documentElement.style.setProperty("--mt-visual-height",`${viewport?.height??window.innerHeight}px`);
    update();
    viewport?.addEventListener("resize",update);
    window.addEventListener("resize",update);
    return ()=>{
      viewport?.removeEventListener("resize",update);
      window.removeEventListener("resize",update);
      document.documentElement.style.removeProperty("--mt-visual-height");
    };
  },[]);
  const messages=status.data?.messages??[];
  useEffect(()=>{if(scrollArea.current)scrollArea.current.scrollTop=scrollArea.current.scrollHeight},[messages.length]);
  const text=draft.roundId===status.data?.roundId?draft.text:"";
  const open=Boolean(status.data?.visible&&status.data.open&&status.data.team&&status.data.roundId);

  async function send(){
    if(!open||!text.trim()||sending)return;
    setSending(true);setError("");
    try {
      await mtApi.submit(status.data!.roundId!,text);
      setDraft({roundId:status.data!.roundId??null,text:""});
      await client.invalidateQueries({queryKey:["mt","typing"]});
    } catch(e) {setError(errorMessage(e))}
    finally{setSending(false)}
  }
  return <MtLayout>{()=> <>
    {status.isPending?<p className="text-sm text-slate-500">불러오는 중...</p>:!status.data?.visible?<MtLocked/>:
      <div className="mt-chat-frame flex h-full min-h-0 flex-col overflow-hidden rounded-3xl border border-[#F3D8D2] bg-white">
        <div className="flex shrink-0 items-center justify-between border-b border-slate-100 px-4 py-3">
          <span className="text-sm font-semibold text-slate-800">{status.data.team?.name??"조 배정 필요"}</span>
          <span className="text-xs font-medium text-slate-500">{status.data.open?"전송 가능":"대기 중"}</span>
        </div>
        <div ref={scrollArea} className="mt-chat-messages flex min-h-0 flex-1 flex-col gap-3 overflow-y-auto px-3 py-4" aria-label="내 메시지">
          {messages.length===0&&<p className="m-auto text-center text-sm text-slate-400">아직 전송한 메시지가 없습니다.</p>}
          {messages.map((m,i)=><div key={m.id} className="flex justify-end">
            <div className="max-w-[88%]">
              <div className="mt-chat-bubble whitespace-pre-wrap break-words rounded-2xl rounded-tr-sm px-3 py-2.5 text-sm leading-relaxed text-slate-900">{m.content}</div>
              <p className="mt-1 text-right text-[11px] text-slate-400">{i===messages.length-1?"마지막 전송":`${i+1}번째 전송`}</p>
            </div>
          </div>)}
        </div>
        {error&&<div className="px-3 pt-2"><MtError message={error}/></div>}
        <div className="mt-chat-composer shrink-0 border-t border-[#F3D8D2] bg-white px-3 py-3">
          <div className="flex items-end gap-2 rounded-2xl border border-slate-200 bg-slate-50 p-1.5">
            <textarea aria-label="메시지 입력" value={text} disabled={!open||sending} maxLength={500}
              onChange={e=>setDraft({roundId:status.data?.roundId??null,text:e.target.value})} rows={1} placeholder={open?"메시지 입력":"전송 대기 중"}
              className="max-h-28 min-h-10 min-w-0 flex-1 resize-none border-none bg-transparent px-2 py-2 text-base text-slate-900 outline-none placeholder:text-slate-400 disabled:opacity-50"/>
            <button type="button" aria-label="전송" onClick={send} disabled={!open||sending||!text.trim()}
              className="mt-send-button flex h-10 w-10 shrink-0 items-center justify-center rounded-xl text-white disabled:bg-slate-300">
              <svg className="h-5 w-5" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24" aria-hidden><path d="m4 12 16-8-4 16-4-7-8-1Z" strokeLinejoin="round" strokeLinecap="round"/></svg>
            </button>
          </div>
        </div>
      </div>}
  </>}</MtLayout>;
}
