"use client";

import { Suspense } from "react";
import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { announcementApi, errorMessage } from "@/lib/api";
import { formatAnnouncementDate } from "@/lib/announcement-ui";

export default function AnnouncementDetailPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <Suspense fallback={<NoticeMessage>공지를 불러오는 중...</NoticeMessage>}>
            <AnnouncementDetailContent />
          </Suspense>
        </AppShell>
      )}
    </AuthGate>
  );
}

function AnnouncementDetailContent() {
  const searchParams = useSearchParams();
  const rawId = searchParams.get("id");
  const announcementId = rawId === null ? NaN : Number(rawId);
  const validId = Number.isSafeInteger(announcementId) && announcementId > 0;

  const query = useQuery({
    queryKey: ["announcements"],
    queryFn: announcementApi.list,
    enabled: validId,
  });

  if (!validId) {
    return <MissingNotice />;
  }
  if (query.isPending) {
    return <NoticeMessage>공지를 불러오는 중...</NoticeMessage>;
  }
  if (query.isError) {
    return <p className="error-box">{errorMessage(query.error)}</p>;
  }

  const announcement = query.data?.find((item) => item.id === announcementId);
  if (!announcement) {
    return <MissingNotice />;
  }

  return (
    <article className="app-card min-w-0 overflow-hidden">
      <div className="flex min-w-0 flex-wrap items-center justify-between gap-2">
        {announcement.pinned && (
          <span className="rounded-full bg-slate-900 px-2.5 py-1 text-xs font-bold text-white">
            고정
          </span>
        )}
        <time
          dateTime={announcement.createdAt}
          className="text-xs text-slate-400"
        >
          {formatAnnouncementDate(announcement.createdAt)}
        </time>
      </div>
      <h1 className="mt-4 min-w-0 break-all text-2xl font-black leading-9 text-slate-950">
        {announcement.title}
      </h1>
      <p className="mt-3 break-all text-sm text-slate-500">
        작성 {announcement.authorName}
      </p>
      <div className="mt-6 border-t border-slate-100 pt-6">
        <p className="min-w-0 whitespace-pre-wrap break-all text-sm leading-7 text-slate-700">
          {announcement.content}
        </p>
      </div>
      <Link href="/announcements" className="secondary-button mt-7">
        목록으로
      </Link>
    </article>
  );
}

function NoticeMessage({ children }: { children: React.ReactNode }) {
  return <p className="app-card text-center text-sm text-slate-500">{children}</p>;
}

function MissingNotice() {
  return (
    <div className="app-card text-center">
      <p className="text-sm text-slate-600">해당 공지를 찾을 수 없습니다.</p>
      <Link href="/announcements" className="secondary-button mt-4">
        공지 목록으로
      </Link>
    </div>
  );
}
