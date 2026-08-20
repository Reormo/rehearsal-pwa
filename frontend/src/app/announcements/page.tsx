"use client";

import { useQuery } from "@tanstack/react-query";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { announcementApi, errorMessage } from "@/lib/api";

export default function AnnouncementsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <AnnouncementsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AnnouncementsContent() {
  const query = useQuery({
    queryKey: ["announcements"],
    queryFn: announcementApi.list,
  });

  return (
    <div className="space-y-6">
      <section>
        <p className="eyebrow">NOTICE</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          공지
        </h1>
        <p className="mt-3 text-sm text-slate-500">
          동아리 운영과 합주 관련 공지를 확인합니다.
        </p>
      </section>

      {query.isPending && <Message>공지를 불러오는 중...</Message>}
      {query.isError && <p className="error-box">{errorMessage(query.error)}</p>}
      {query.data?.length === 0 && <Message>등록된 공지가 없습니다.</Message>}

      <div className="space-y-4">
        {query.data?.map((announcement) => (
          <article key={announcement.id} className="app-card">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div>
                {announcement.pinned && (
                  <span className="inline-flex rounded-full bg-slate-900 px-2.5 py-1 text-xs font-bold text-white">
                    고정
                  </span>
                )}
                <h2 className="mt-2 text-xl font-bold text-slate-950">
                  {announcement.title}
                </h2>
              </div>
              <p className="text-xs text-slate-400">
                {formatDate(announcement.createdAt)}
              </p>
            </div>
            <p className="mt-4 whitespace-pre-wrap text-sm leading-7 text-slate-700">
              {announcement.content}
            </p>
            <p className="mt-4 text-xs text-slate-400">
              작성 {announcement.authorName}
            </p>
          </article>
        ))}
      </div>
    </div>
  );
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
