import { request } from "@/lib/api";

export type MtTeam = { id: number; name: string; members: { id: number; name: string }[] };
export type MtQuestion = { id: number; text: string; sort_order: number };
export type MtState = { exists: boolean; eventTitle?: string; buzzerVisible: boolean; typingVisible: boolean; host: boolean; superAdmin: boolean; team?: {id:number;name:string}|null };
export type MtManager = {id:number;name:string;role:"MEMBER"|"ADMIN"|"SUPER_ADMIN";assigned:boolean;permanent:boolean};
export type MtHistoryItem = {id:number;title:string;created_at:string;ended_at:string;team_count:number;question_count:number;submission_count:number};
export type MtHistoryDetail = {id:number;title:string;endedAt:string;
  teams:{id:number;name:string;member_count:number}[];
  questions:{id:number;text:string}[];
  rounds:{id:number;answer_snapshot:string;started_at:string;ended_at:string}[];
  submissions:{id:number;user_name:string;team_name:string;content:string;submitted_at:string}[]};
export type MtHost = { exists: boolean; eventTitle?:string; buzzerVisible?:boolean; typingVisible?:boolean; buzzerOpen?:boolean; buzzerPaused?:boolean; winnerTeamId?:number|null; winnerTeamName?:string|null; excludedTeamIds?:number[]; typingOpen?:boolean; currentRoundId?:number|null };
export type MtBuzz = { visible:boolean; open?:boolean; paused?:boolean; myTeam?:{id:number;name:string}|null; winnerTeamId?:number|null; winnerTeamName?:string|null; excludedTeamIds?:number[] };
export type MtTyping = { visible:boolean; open?:boolean; team?:{id:number;name:string}|null; roundId?:number|null; messages?:{id:number;content:string;submitted_at:string}[] };
export type MtSubmission = { id:number;user_id:number;user_name:string;team_name:string;content:string;correct:boolean;latest:boolean;submitted_at:string };
export type MtResults = { roundId:number; questionId?:number; open?:boolean; ranking:{name:string;teamName:string}[]; submissions:MtSubmission[] };

const post=(path:string,body?:object)=>request<void>("/api/mt"+path,{method:"POST",...(body?{body:JSON.stringify(body)}:{})});
const patch=(path:string,body:object)=>request<void>("/api/mt"+path,{method:"PATCH",body:JSON.stringify(body)});
const del=(path:string)=>request<void>("/api/mt"+path,{method:"DELETE"});

export const mtApi = {
  state:()=>request<MtState>("/api/mt/state"),
  host:()=>request<MtHost>("/api/mt/host"),
  managers:()=>request<MtManager[]>("/api/mt/host/managers"),
  grantManager:(id:number)=>request<void>(`/api/mt/host/managers/${id}`,{method:"POST"}),
  revokeManager:(id:number)=>request<void>(`/api/mt/host/managers/${id}`,{method:"DELETE"}),
  createEvent:(title:string)=>request<MtHost>("/api/mt/host/events",{method:"POST",body:JSON.stringify({title})}),
  endEvent:()=>post("/host/events/end"),
  history:()=>request<MtHistoryItem[]>("/api/mt/host/events/history"),
  historyDetail:(id:number)=>request<MtHistoryDetail>(`/api/mt/host/events/${id}/history`),
  restoreEvent:(id:number)=>post(`/host/events/${id}/restore`),
  deleteArchivedEvent:(id:number)=>del(`/host/events/${id}`),
  teams:()=>request<MtTeam[]>("/api/mt/host/teams"),
  members:()=>request<{id:number;name:string}[]>("/api/mt/host/members"),
  addTeam:(name:string)=>post("/host/teams",{name}),
  renameTeam:(id:number,name:string)=>patch(`/host/teams/${id}`,{name}),
  deleteTeam:(id:number)=>del(`/host/teams/${id}`),
  assign:(teamId:number,userId:number)=>request<void>(`/api/mt/host/teams/${teamId}/members/${userId}`,{method:"PUT"}),
  unassign:(teamId:number,userId:number)=>del(`/host/teams/${teamId}/members/${userId}`),
  questions:()=>request<MtQuestion[]>("/api/mt/host/questions"),
  addQuestion:(text:string)=>post("/host/questions",{text}),
  editQuestion:(id:number,text:string)=>patch(`/host/questions/${id}`,{text}),
  deleteQuestion:(id:number)=>del(`/host/questions/${id}`),
  moveQuestion:(id:number,direction:"UP"|"DOWN")=>post(`/host/questions/${id}/move`,{direction}),
  visibility:(game:"BUZZER"|"TYPING",enabled:boolean)=>post("/host/visibility",{game,enabled}),
  buzzer:()=>request<MtBuzz>("/api/mt/buzzer"),
  press:()=>request<{won:boolean}>("/api/mt/buzzer/press",{method:"POST"}),
  buzzerAction:(action:"OPEN"|"CLOSE"|"OTHER"|"RETRY"|"RESET")=>post("/host/buzzer",{action}),
  typing:()=>request<MtTyping>("/api/mt/typing"),
  submit:(roundId:number,text:string)=>post("/typing",{roundId,text}),
  startTyping:(questionId:number)=>post("/host/typing/start",{questionId}),
  closeTyping:()=>post("/host/typing/close"),
  results:()=>request<MtResults>("/api/mt/host/typing/results"),
};
