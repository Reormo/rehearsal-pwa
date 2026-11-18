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

  it("does not disable unrelated controls while a visibility change is pending",async()=>{
    vi.spyOn(mtApi,"visibility").mockImplementation(()=>new Promise<void>(()=>{}));
    render(<QueryClientProvider client={queryClient}><MtHostPage/></QueryClientProvider>);
    const buzzer=await screen.findByRole("switch",{name:"버저비터 공개"});
    const typing=screen.getByRole("switch",{name:"문자 빨리 보내 공개"});
    fireEvent.click(buzzer);
    await waitFor(()=>expect(buzzer).toBeDisabled());
    expect(typing).not.toBeDisabled();
    expect(screen.getByRole("button",{name:"버저 진행"})).not.toBeDisabled();
    expect(screen.getByText("조 관리 →")).toBeInTheDocument();
  });
});
