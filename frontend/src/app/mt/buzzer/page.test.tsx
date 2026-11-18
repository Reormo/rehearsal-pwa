"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import MtBuzzerPage from "./page";
import { mtApi } from "@/lib/mt-api";

vi.mock("@/components/mt-layout",()=>({
  MtLayout:({children}:{children:(u:{role:string})=>React.ReactNode})=>children({role:"MEMBER"}),
  MtLoading:()=> <div role="status">로딩</div>,
  MtError:({message}:{message:string})=><div role="alert">{message}</div>,
}));
describe("MT buzzer single-button screen",()=>{
  let client:QueryClient;
  beforeEach(()=>{
    client=new QueryClient({defaultOptions:{queries:{retry:false}}});
    vi.spyOn(mtApi,"buzzer").mockResolvedValue({visible:true,open:false,myTeam:null});
  });
  afterEach(()=>{cleanup();client.clear();vi.restoreAllMocks();});
  it("shows no surrounding card or redundant visible status",async()=>{
    render(<QueryClientProvider client={client}><MtBuzzerPage/></QueryClientProvider>);
    const button=await screen.findByRole("button",{name:"버저 대기 중"});
    expect(button).toHaveTextContent("버저비터");
    expect(button).toBeDisabled();
    expect(document.querySelector(".mt-card")).toBeNull();
    expect(screen.queryByText("대기 중")).not.toBeInTheDocument();
  });
});
