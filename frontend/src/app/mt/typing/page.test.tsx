"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
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
    const textbox=screen.getByRole("textbox",{name:"메시지 입력"});
    expect(textbox).toBeDisabled();
    expect(textbox).toHaveAttribute("placeholder","MC가 입력을 허용하면 시작할 수 있어요");
    expect(screen.getByRole("button",{name:"전송"})).toBeDisabled();
    expect(screen.queryByText("전송 대기 중")).not.toBeInTheDocument();
    expect(screen.queryByText("조 배정 필요")).not.toBeInTheDocument();
    const frame=document.querySelector(".mt-chat-frame");
    expect(frame).not.toBeNull();
    expect(frame?.classList.contains("rounded-3xl")).toBe(false);
    expect(screen.getByLabelText("내 메시지")).toBeInTheDocument();
    expect(document.querySelector(".mt-chat-composer")).not.toBeNull();
  });

  it("enables typing and sending only after MC starts a round",async()=>{
    const submit=vi.spyOn(mtApi,"submit").mockResolvedValue(undefined);
    const view=render(<QueryClientProvider client={client}><MtTypingPage/></QueryClientProvider>);
    const textbox=await screen.findByRole("textbox",{name:"메시지 입력"});
    expect(textbox).toBeDisabled();

    vi.spyOn(mtApi,"typing").mockResolvedValue({
      visible:true,open:true,team:{id:1,name:"1조"},roundId:3,messages:[],
    });
    await act(async()=>{await client.invalidateQueries({queryKey:["mt","typing"]});});
    await waitFor(()=>expect(textbox).not.toBeDisabled());
    expect(textbox).toHaveAttribute("placeholder","메시지 입력");
    fireEvent.change(textbox,{target:{value:"테스트 문자"}});
    const send=screen.getByRole("button",{name:"전송"});
    expect(send).not.toBeDisabled();
    fireEvent.click(send);
    await waitFor(()=>expect(submit).toHaveBeenCalledWith(3,"테스트 문자"));
    view.unmount();
  });

  it("keeps input disabled when the MC has started but the member has no team",async()=>{
    vi.spyOn(mtApi,"typing").mockResolvedValue({
      visible:true,open:true,team:null,roundId:3,messages:[],
    });
    const view=render(<QueryClientProvider client={client}><MtTypingPage/></QueryClientProvider>);
    const textbox=await screen.findByRole("textbox",{name:"메시지 입력"});
    expect(textbox).toBeDisabled();
    expect(textbox).toHaveAttribute("placeholder","조 배정 후 입력할 수 있어요");
    expect(screen.getByRole("button",{name:"전송"})).toBeDisabled();
    view.unmount();
  });
});
