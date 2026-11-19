"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MtHostPage from "./page";
import { mtApi } from "@/lib/mt-api";

vi.mock("@/components/mt-layout",()=>({
  MtLayout:({children}:{children:(user:{role:string})=>React.ReactNode})=>children({role:"SUPER_ADMIN"}),
  MtCard:({children}:{children:React.ReactNode})=><section>{children}</section>,
  MtError:({message}:{message:string})=><div role="alert">{message}</div>,
  MtLoading:()=> <div role="status">불러오는 중</div>,
  MtSwitch:({label,checked,disabled,onChange}:{
    label:string;checked:boolean;disabled:boolean;onChange:(v:boolean)=>void
  })=><button type="button" role="switch" aria-label={label} aria-checked={checked}
    disabled={disabled} onClick={()=>onChange(!checked)}>{label}</button>,
}));

describe("MT host visibility switches",()=>{
  let queryClient:QueryClient;
  beforeEach(()=>{
    queryClient=new QueryClient({defaultOptions:{queries:{retry:false}}});
    vi.spyOn(mtApi,"host").mockResolvedValue({
      exists:true,eventTitle:"무혼 MT",buzzerVisible:false,typingVisible:false,
      buzzerOpen:false,buzzerPaused:false,typingOpen:false,currentRoundId:null,
    });
    vi.spyOn(mtApi,"teams").mockResolvedValue([]);
    vi.spyOn(mtApi,"questions").mockResolvedValue([]);
  });
  afterEach(()=>{cleanup();queryClient.clear();vi.restoreAllMocks();});

  it("shows only published games as MC tabs",async()=>{
    render(<QueryClientProvider client={queryClient}><MtHostPage/></QueryClientProvider>);
    await screen.findByRole("switch",{name:"버저비터 공개"});
    expect(screen.queryByRole("navigation",{name:"MT 게임 진행 선택"})).not.toBeInTheDocument();
    queryClient.setQueryData(["mt","host"],{
      exists:true,eventTitle:"무혼 MT",buzzerVisible:true,typingVisible:false,
      buzzerOpen:false,buzzerPaused:false,typingOpen:false,currentRoundId:null,
    });
    expect(await screen.findByRole("button",{name:"버저비터"})).toBeInTheDocument();
    expect(screen.queryByRole("button",{name:"문자 빨리 보내!"})).not.toBeInTheDocument();
  });

  it("shows submitted time instead of latest/previous labels in MC results",async()=>{
    vi.spyOn(mtApi,"host").mockResolvedValue({
      exists:true,eventTitle:"무혼 MT",buzzerVisible:false,typingVisible:true,
      buzzerOpen:false,buzzerPaused:false,typingOpen:true,currentRoundId:10,
    });
    vi.spyOn(mtApi,"questions").mockResolvedValue([{id:3,text:"테스트 문장",sort_order:1}]);
    vi.spyOn(mtApi,"results").mockResolvedValue({
      roundId:10,questionId:3,open:true,ranking:[],
      submissions:[{id:99,user_id:1,user_name:"테스트 회원",team_name:"1조",content:"문자 제출",correct:true,latest:true,submitted_at:"2026-11-18T07:38:00Z"}],
    });
    render(<QueryClientProvider client={queryClient}><MtHostPage/></QueryClientProvider>);
    fireEvent.click(await screen.findByRole("button",{name:"문자 빨리 보내!"}));
    expect(await screen.findByText("16:38")).toBeInTheDocument();
    expect(screen.queryByText("최신")).not.toBeInTheDocument();
    expect(screen.queryByText("이전")).not.toBeInTheDocument();
    expect(screen.getByText("정답")).toBeInTheDocument();
  });

  it("does not disable unrelated controls while a visibility change is pending",async()=>{
    vi.spyOn(mtApi,"visibility").mockImplementation(()=>new Promise<void>(()=>{}));
    render(<QueryClientProvider client={queryClient}><MtHostPage/></QueryClientProvider>);
    const buzzer=await screen.findByRole("switch",{name:"버저비터 공개"});
    const typing=screen.getByRole("switch",{name:"문자 빨리 보내 공개"});
    fireEvent.click(buzzer);
    await waitFor(()=>expect(buzzer).toBeDisabled());
    expect(typing).not.toBeDisabled();
    // Unrelated controls remain available while the buzzer visibility request is pending.
    expect(screen.getByRole("button",{name:"행사 종료"})).not.toBeDisabled();
    expect(screen.getByRole("link",{name:"조 관리"})).toBeInTheDocument();
  });
});
