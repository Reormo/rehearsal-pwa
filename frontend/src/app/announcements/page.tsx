"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { announcementApi, errorMessage } from "@/lib/api";
import { announcementPreview, formatAnnouncementDate } from "@/lib/announcement-ui";

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
    <div className="space-y-4">
      {query.isPending && <Message>공지를 불러오는 중...</Message>}
      {query.isError && <p className="error-box">{errorMessage(query.error)}</p>}
      {query.data?.length === 0 && <Message>등록된 공지가 없습니다.</Message>}

      {query.data?.map((announcement) => (
        <Link
          key={announcement.id}
          href={`/announcements/detail?id=${announcement.id}`}
          className="app-card block min-w-0 overflow-hidden transition-[border-color,box-shadow] hover:border-slate-300 hover:shadow-lg focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
        >
          <div className="flex min-w-0 flex-wrap items-center justify-between gap-x-3 gap-y-2">
            {announcement.pinned ? (
              <span className="rounded-full bg-slate-900 px-2.5 py-1 text-xs font-bold text-white">
                고정
              </span>
            ) : (
              <span aria-hidden="true" />
            )}
            <time className="text-xs text-slate-400" dateTime={announcement.createdAt}>
              {formatAnnouncementDate(announcement.createdAt)}
            </time>
          </div>

          <h2 className="mt-3 line-clamp-2 min-w-0 break-all text-lg font-bold leading-7 text-slate-950">
            {announcement.title}
          </h2>
          <p className="mt-2 line-clamp-2 min-w-0 break-all text-sm leading-6 text-slate-500">
            {announcementPreview(announcement.content)}
          </p>
        </Link>
      ))}
    </div>
  );
}

function Message({ children }: { children: React.ReactNode }) {
  return <p className="app-card text-center text-sm text-slate-400">{children}</p>;
}
