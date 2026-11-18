"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MtTypingPage from "./page";
import { mtApi } from "@/lib/mt-api";

vi.mock("@/components/mt-layout",()=>({
  MtLayout:({children}:{children:(u:{role:string})=>React.ReactNode})=>children({role:"MEMBER"}),
  MtLoading:()=> <div role="status">로딩</div>,
  MtError:({message}:{message:string})=><div role="alert">{message}</div>,
}));
describe("MT chat messenger layout",()=>{
  let client:QueryClient;
  beforeEach(()=>{
    client=new QueryClient({defaultOptions:{queries:{retry:false}}});
    vi.spyOn(mtApi,"typing").mockResolvedValue({
      visible:true,open:false,team:{id:1,name:"1조"},roundId:2,
      messages:[{id:2,content:"메시지 테스트",submitted_at:"2026-11-18T00:00:00Z"}],
    });
  });
  afterEach(()=>{cleanup();client.clear();vi.restoreAllMocks();});
  it("renders only bubbles and a fixed input area, without the old card",async()=>{
    render(<QueryClientProvider client={client}><MtTypingPage/></QueryClientProvider>);
    expect(await screen.findByText("메시지 테스트")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("메시지 입력")).toBeDisabled();
    expect(screen.queryByText("전송 대기 중")).not.toBeInTheDocument();
    expect(screen.queryByText("조 배정 필요")).not.toBeInTheDocument();
    const frame=document.querySelector(".mt-chat-frame");
    expect(frame).not.toBeNull();
    expect(frame?.classList.contains("rounded-3xl")).toBe(false);
  });
});
