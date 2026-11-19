"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mtApi, type MtState } from "@/lib/mt-api";
import { MtLayout, MtGameCard, MtLoading } from "./mt-layout";
vi.mock("next/navigation",()=>({usePathname:()=>"/mt/host"}));
vi.mock("@/components/auth-gate",()=>({
  AuthGate:({children}:{children:(user:{id:number;name:string;clubId:number;loginId:string;role:"MEMBER"})=>React.ReactNode})=>
    children({id:7,name:"MT 회원",clubId:1,loginId:"mt-member",role:"MEMBER"}),
}));
vi.mock("@/components/app-shell",()=>({
  AppShell:({children}:{children:React.ReactNode})=><div>{children}</div>,
}));
describe("MT role gate",()=>{
  let client:QueryClient;
  beforeEach(()=>{client=new QueryClient({defaultOptions:{queries:{retry:false}}});});
  afterEach(()=>{cleanup();client.clear();vi.restoreAllMocks();});
  function view(){return render(<QueryClientProvider client={client}>
    <MtLayout hostOnly>{()=> <div>MC 화면</div>}</MtLayout>
  </QueryClientProvider>);}
  const state:MtState={exists:false,buzzerVisible:false,typingVisible:false,host:false,superAdmin:false};
  it("allows a designated member to host",async()=>{
    vi.spyOn(mtApi,"state").mockResolvedValue({...state,host:true});
    view();expect(await screen.findByText("MC 화면")).toBeInTheDocument();
  });
  it("keeps locked game cards the same size without revealing hidden game titles",()=>{
    render(<>
      <MtGameCard visible href="/mt/buzzer" kind="buzzer" title="버저비터"/>
      <MtGameCard visible={false} href="/mt/typing" kind="typing" title="문자 빨리 보내!"/>
    </>);
    expect(screen.getByText("버저비터")).toBeInTheDocument();
    expect(screen.queryByText("문자 빨리 보내!")).not.toBeInTheDocument();
    expect(document.querySelectorAll(".mt-game-tile")).toHaveLength(2);
    expect(screen.getByText("잠겨 있는 기능")).toBeInTheDocument();
  });
  it("renders the MT loading spinner with an accessible label",()=>{
    render(<MtLoading/>);
    expect(screen.getByRole("status",{name:"MT 정보를 불러오는 중"})).toBeInTheDocument();
  });
  it("blocks an unassigned member",async()=>{
    vi.spyOn(mtApi,"state").mockResolvedValue(state);
    view();
    expect(await screen.findByText("MT 관리자 권한이 없습니다.")).toBeInTheDocument();
    expect(screen.queryByText("MC 화면")).not.toBeInTheDocument();
  });
});
