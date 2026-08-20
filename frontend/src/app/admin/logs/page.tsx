"use client";

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { AdminActionLog, adminApi, errorMessage } from "@/lib/api";

export default function AdminLogsPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminLogsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminLogsContent() {
  const query = useQuery({
    queryKey: ["admin", "action-logs"],
    queryFn: () => adminApi.actionLogs(100),
  });

  return (
    <div className="space-y-6">
      <section>
        <p className="eyebrow">ADMIN · AUDIT</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          관리자 작업 이력
        </h1>
        <p className="mt-3 text-sm text-slate-500">
          최근 관리자 변경 작업 100건을 확인합니다.
        </p>
      </section>

      {query.isPending && <Message>작업 이력을 불러오는 중...</Message>}
      {query.isError && <p className="error-box">{errorMessage(query.error)}</p>}
      {query.data?.length === 0 && <Message>아직 기록된 작업이 없습니다.</Message>}

      <div className="space-y-3">
        {query.data?.map((log) => <LogRow key={log.id} log={log} />)}
      </div>
    </div>
  );
}

function LogRow({ log }: { log: AdminActionLog }) {
  return (
    <article className="app-card">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="font-bold text-slate-950">{actionLabel(log.actionType)}</p>
          <p className="mt-1 text-sm text-slate-500">
            {log.actorName} · {log.targetType}
            {log.targetId !== null ? ` #${log.targetId}` : ""}
          </p>
          {log.reason && (
            <p className="mt-2 text-sm text-slate-600">사유: {log.reason}</p>
          )}
        </div>
        <time className="text-xs text-slate-400">{formatDate(log.createdAt)}</time>
      </div>

      {(log.beforeData || log.afterData) && (
        <details className="mt-4 rounded-2xl bg-slate-50 p-4">
          <summary className="cursor-pointer text-sm font-semibold text-slate-600">
            변경 데이터 보기
          </summary>
          <div className="mt-3 grid gap-3 lg:grid-cols-2">
            <Snapshot title="변경 전" value={log.beforeData} />
            <Snapshot title="변경 후" value={log.afterData} />
          </div>
        </details>
      )}
    </article>
  );
}

function Snapshot({
  title,
  value,
}: {
  title: string;
  value: Record<string, unknown> | null;
}) {
  return (
    <div>
      <p className="text-xs font-bold text-slate-400">{title}</p>
      <pre className="mt-2 overflow-x-auto whitespace-pre-wrap text-xs leading-5 text-slate-600">
        {value ? JSON.stringify(value, null, 2) : "-"}
      </pre>
    </div>
  );
}

function actionLabel(actionType: string) {
  const labels: Record<string, string> = {
    SIGNUP_APPROVE: "가입 신청 승인",
    SIGNUP_REJECT: "가입 신청 거절",
    INVITE_CODE_ROTATE: "초대코드 재발급",
    MEMBER_ROLE_CHANGE: "회원 권한 변경",
    MEMBER_PASSWORD_RESET: "회원 비밀번호 초기화",
    MEMBER_DELETE: "회원 삭제",
    SONG_CREATE: "곡 생성",
    SONG_RENAME: "곡 제목 변경",
    SONG_ARCHIVE: "곡 보관",
    SONG_RESTORE: "곡 복구",
    SONG_MEMBER_ADD: "곡 참여자 추가",
    SONG_MEMBER_SESSION_CHANGE: "곡 세션 변경",
    SONG_MEMBER_REMOVE: "곡 참여자 제외",
    SONG_LEADER_CHANGE: "곡 팀장 변경",
    ANNOUNCEMENT_CREATE: "공지 작성",
    ANNOUNCEMENT_UPDATE: "공지 수정",
    ANNOUNCEMENT_DELETE: "공지 삭제",
  };
  return labels[actionType] ?? actionType;
}

function Message({ children }: { children: React.ReactNode }) {
  return (
    <p className="app-card text-center text-sm text-slate-400">{children}</p>
  );
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(value));
}
