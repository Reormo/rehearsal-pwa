"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mtApi, type MtState } from "@/lib/mt-api";
import { MtLayout } from "./mt-layout";
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
  it("blocks an unassigned member",async()=>{
    vi.spyOn(mtApi,"state").mockResolvedValue(state);
    view();
    expect(await screen.findByText("MT 관리자 권한이 없습니다.")).toBeInTheDocument();
    expect(screen.queryByText("MC 화면")).not.toBeInTheDocument();
  });
});
