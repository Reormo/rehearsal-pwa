"use client";

import { useRef, useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { MtLayout, MtLocked, MtError } from "@/components/mt-layout";
import { errorMessage } from "@/lib/api";
import { mtApi } from "@/lib/mt-api";

function playSuccess(ctx:AudioContext|null) {
  if(ctx) {
    try {
      const tone=ctx.createOscillator(), gain=ctx.createGain();
      tone.type="sine"; tone.frequency.setValueAtTime(660,ctx.currentTime);
      tone.frequency.setValueAtTime(880,ctx.currentTime+0.1);
      gain.gain.setValueAtTime(0.12,ctx.currentTime);
      gain.gain.exponentialRampToValueAtTime(0.001,ctx.currentTime+0.28);
      tone.connect(gain);gain.connect(ctx.destination);
      tone.start();tone.stop(ctx.currentTime+0.3);
      window.setTimeout(()=>{void ctx.close()},550);
    } catch { /* Audio feedback is optional. */ }
  }
  if("vibrate" in navigator) navigator.vibrate(160);
}

export default function MtBuzzerPage() {
  const client=useQueryClient();
  const status=useQuery({queryKey:["mt","buzzer"],queryFn:mtApi.buzzer,refetchInterval:650});
  const [sending,setSending]=useState(false);
  const [error,setError]=useState("");
  const ctxRef=useRef<AudioContext|null>(null);
  const myId=status.data?.myTeam?.id;
  const won=myId!=null&&status.data?.winnerTeamId===myId&&!status.data?.paused;
  const excluded=myId!=null&&status.data?.excludedTeamIds?.includes(myId);
  const canPress=Boolean(status.data?.visible&&status.data.open&&myId&&!excluded&&!sending);

  async function press() {
    if(!canPress)return;
    setSending(true);setError("");
    try {
      // Prime audio in the button's direct tap gesture (iOS browser requirement).
      if(typeof window.AudioContext!=="undefined") {
        ctxRef.current=new AudioContext();
        void ctxRef.current.resume();
      }
      const result=await mtApi.press();
      if(result.won) playSuccess(ctxRef.current);
      else if(ctxRef.current)void ctxRef.current.close();
      await client.invalidateQueries({queryKey:["mt","buzzer"]});
    } catch(e) {
      setError(errorMessage(e));
      if(ctxRef.current)void ctxRef.current.close();
    } finally {setSending(false);}
  }

  return <MtLayout>{()=> <>
    {status.isPending?<p className="text-sm text-slate-500">불러오는 중...</p>:
      !status.data?.visible?<MtLocked/>:
      <div className="mt-card flex min-h-[55dvh] flex-col items-center justify-between gap-8 rounded-3xl px-5 py-6">
        <div className="w-full text-right text-sm font-semibold text-slate-500">{status.data.myTeam?.name??"조 배정 필요"}</div>
        <div className="flex w-full flex-col items-center gap-7">
          <p aria-live="polite" className={`text-lg font-bold ${won?"text-[#CB465B]":"text-slate-800"}`}>
            {status.data.paused?"대기 중":won?"버저 성공!":excluded?"대기 중":status.data.winnerTeamName?status.data.winnerTeamName:status.data.open?"버저를 누르세요":"대기 중"}
          </p>
          <button type="button" disabled={!canPress} onClick={press}
            className={`flex h-52 w-52 touch-manipulation select-none items-center justify-center rounded-full text-3xl font-black text-white shadow-lg transition-transform active:scale-95 disabled:shadow-none ${won?"mt-buzz-success":canPress?"mt-buzz-active":"bg-slate-300"}`}>
            {won?"성공!":"BUZZ!"}
          </button>
        </div>
        <div className="min-h-8 w-full">{error&&<MtError message={error}/>}</div>
      </div>}
  </>}</MtLayout>;
}
