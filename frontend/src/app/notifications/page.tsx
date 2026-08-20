"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { useEffect } from "react";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import { AppNotification, notificationApi } from "@/lib/notification-api";

export default function NotificationsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <NotificationsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function NotificationsContent() {
  const queryClient = useQueryClient();
  const notificationsQuery = useQuery({
    queryKey: ["notifications"],
    queryFn: notificationApi.list,
  });
  const markAllReadMutation = useMutation({
    mutationFn: notificationApi.markAllRead,
    onMutate: async () => {
      queryClient.setQueryData(["notifications", "unread-count"], { count: 0 });
    },
    onError: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["notifications", "unread-count"],
      });
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({
        queryKey: ["notifications", "unread-count"],
      });
    },
  });
  const dismissMutation = useMutation({
    mutationFn: notificationApi.dismiss,
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["notifications"] }),
        queryClient.invalidateQueries({
          queryKey: ["notifications", "unread-count"],
        }),
      ]);
    },
  });

  useEffect(() => {
    markAllReadMutation.mutate();
    // Opening the warehouse is the read action. The mutation is idempotent.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div className="space-y-7">
      <section>
        <p className="eyebrow">NOTIFICATIONS</p>
        <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
          알림 창고
        </h1>
        <p className="mt-3 text-sm leading-6 text-slate-500">
          알림 창고를 열면 하단 메뉴의 미확인 숫자는 사라집니다. 알림 내용은
          그대로 남아 있고, 더 이상 보관하지 않을 알림만 오른쪽 X로 없앨 수 있습니다.
        </p>
      </section>

      <section className="app-card">
        {notificationsQuery.isPending && (
          <p className="text-sm text-slate-400">알림을 불러오는 중...</p>
        )}
        {notificationsQuery.isError && (
          <p className="error-box">{errorMessage(notificationsQuery.error)}</p>
        )}
        {markAllReadMutation.isError && (
          <p className="error-box mb-4">미확인 알림 처리를 완료하지 못했습니다.</p>
        )}
        {dismissMutation.isError && (
          <p className="error-box mb-4">{errorMessage(dismissMutation.error)}</p>
        )}
        {notificationsQuery.data?.length === 0 && (
          <p className="rounded-2xl bg-slate-50 px-4 py-8 text-center text-sm text-slate-500">
            보관 중인 알림이 없습니다.
          </p>
        )}
        {notificationsQuery.data && notificationsQuery.data.length > 0 && (
          <div className="space-y-3">
            {notificationsQuery.data.map((notification) => (
              <div
                key={notification.id}
                className="flex items-start gap-3 rounded-2xl border border-slate-200 p-4"
              >
                {notification.linkPath ? (
                  <Link href={notification.linkPath} className="min-w-0 flex-1">
                    <NotificationText notification={notification} />
                  </Link>
                ) : (
                  <div className="min-w-0 flex-1">
                    <NotificationText notification={notification} />
                  </div>
                )}
                <button
                  type="button"
                  aria-label="알림 없애기"
                  className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 text-lg font-bold text-slate-400 transition hover:border-slate-400 hover:text-slate-700"
                  disabled={
                    dismissMutation.isPending &&
                    dismissMutation.variables === notification.id
                  }
                  onClick={() => dismissMutation.mutate(notification.id)}
                >
                  ×
                </button>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}

function NotificationText({ notification }: { notification: AppNotification }) {
  return (
    <>
      <div className="flex flex-wrap items-center gap-2">
        <p className="font-bold text-slate-950">{notification.title}</p>
        {notification.type === "RESERVATION_CANCELED" && (
          <span className="rounded-full bg-red-50 px-2 py-1 text-[11px] font-bold text-red-600">
            예약 취소
          </span>
        )}
      </div>
      <p className="mt-2 text-sm leading-6 text-slate-600">{notification.body}</p>
      <p className="mt-2 text-xs text-slate-400">
        {formatNotificationTime(notification.createdAt)}
      </p>
    </>
  );
}

function formatNotificationTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  }).format(new Date(value));
}
