"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mtApi, type MtState } from "@/lib/mt-api";
import MtPage from "./page";

const navigation=vi.hoisted(()=>({replace:vi.fn()}));
vi.mock("next/navigation",()=>({useRouter:()=>({replace:navigation.replace})}));
vi.mock("@/components/mt-layout",()=>({
  MtLayout:({children}:{children:(user:{role:string})=>React.ReactNode})=>children({role:"MEMBER"}),
  MtHeader:({title}:{title:string})=><h1>{title}</h1>,
  MtLocked:()=> <p>잠겨있는 기능입니다</p>,
}));

describe("MT entry routing",()=>{
  let client:QueryClient;
  beforeEach(()=>{
    navigation.replace.mockReset();
    client=new QueryClient({defaultOptions:{queries:{retry:false}}});
  });
  afterEach(()=>{cleanup();client.clear();vi.restoreAllMocks();});
  function view(){return render(<QueryClientProvider client={client}><MtPage/></QueryClientProvider>);}
  const state:MtState={exists:true,buzzerVisible:true,typingVisible:true,host:false,superAdmin:false};
  it("redirects designated hosts to the MC screen",async()=>{
    vi.spyOn(mtApi,"state").mockResolvedValue({...state,host:true});
    view();
    await waitFor(()=>expect(navigation.replace).toHaveBeenCalledWith("/mt/host"));
    expect(screen.getByText("MT 관리자 화면으로 이동 중...")).toBeInTheDocument();
  });
  it("shows participant games to an ordinary member",async()=>{
    vi.spyOn(mtApi,"state").mockResolvedValue(state);
    view();
    expect(await screen.findByText("버저비터")).toBeInTheDocument();
    expect(navigation.replace).not.toHaveBeenCalled();
  });
});
