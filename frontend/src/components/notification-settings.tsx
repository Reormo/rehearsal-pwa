"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { errorMessage } from "@/lib/api";
import { notificationApi } from "@/lib/notification-api";

const REMINDER_OPTIONS = [
  { value: "off", label: "알림 끄기" },
  { value: "10", label: "10분 전" },
  { value: "30", label: "30분 전" },
  { value: "60", label: "1시간 전" },
  { value: "120", label: "2시간 전" },
  { value: "1440", label: "하루 전" },
];

export function NotificationSettingsCard() {
  const queryClient = useQueryClient();
  const [pushSupported, setPushSupported] = useState(false);
  const [pushEnabledInBrowser, setPushEnabledInBrowser] = useState(false);
  const [pushPermission, setPushPermission] =
    useState<NotificationPermission>("default");
  const [pushBusy, setPushBusy] = useState(false);
  const [pushError, setPushError] = useState("");
  const [pushMessage, setPushMessage] = useState("");

  const settingsQuery = useQuery({
    queryKey: ["notifications", "settings"],
    queryFn: notificationApi.settings,
  });
  const pushConfigQuery = useQuery({
    queryKey: ["notifications", "push-config"],
    queryFn: notificationApi.pushConfig,
  });
  const pushStatusQuery = useQuery({
    queryKey: ["notifications", "push-status"],
    queryFn: notificationApi.pushStatus,
  });

  const settingsMutation = useMutation({
    mutationFn: (minutes: number | null) =>
      notificationApi.updateSettings(minutes),
    onSuccess: (settings) => {
      queryClient.setQueryData(["notifications", "settings"], settings);
    },
  });

  let reminderMinutes: number | null = 30;
  if (settingsMutation.isPending) {
    reminderMinutes = settingsMutation.variables;
  } else if (settingsQuery.data) {
    reminderMinutes = settingsQuery.data.rehearsalReminderMinutes;
  }
  const reminderValue =
    reminderMinutes == null ? "off" : String(reminderMinutes);

  useEffect(() => {
    if (
      typeof window === "undefined" ||
      !("serviceWorker" in navigator) ||
      !("PushManager" in window) ||
      !("Notification" in window)
    ) {
      return;
    }

    void navigator.serviceWorker.getRegistration("/").then((registration) => {
      setPushSupported(true);
      setPushPermission(Notification.permission);
      return registration?.pushManager.getSubscription();
    }).then((subscription) => {
      setPushEnabledInBrowser(Boolean(subscription));
    });
  }, []);

  async function enablePush() {
    setPushBusy(true);
    setPushError("");
    setPushMessage("");

    try {
      const config = await notificationApi.pushConfig();
      if (!config.enabled || !config.publicKey) {
        throw new Error("서버 Web Push 키가 아직 설정되지 않았습니다.");
      }

      const permission = await Notification.requestPermission();
      setPushPermission(permission);
      if (permission !== "granted") {
        throw new Error(
          "브라우저 알림 권한을 허용해야 Push 알림을 켤 수 있습니다.",
        );
      }

      await navigator.serviceWorker.register(
        "/sw.js",
        { scope: "/" },
      );
      const registration = await navigator.serviceWorker.ready;

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(config.publicKey),
        });
      }

      const json = subscription.toJSON();
      const p256dh = json.keys?.p256dh;
      const auth = json.keys?.auth;

      if (!json.endpoint || !p256dh || !auth) {
        throw new Error("브라우저 Push 구독 정보를 읽지 못했습니다.");
      }

      await notificationApi.subscribePush({
        endpoint: json.endpoint,
        p256dh,
        auth,
      });

      setPushEnabledInBrowser(true);
      setPushMessage("이 브라우저의 Push 알림을 켰습니다.");
      await queryClient.invalidateQueries({
        queryKey: ["notifications", "push-status"],
      });
    } catch (error) {
      setPushError(errorMessage(error));
    } finally {
      setPushBusy(false);
    }
  }

  async function disablePush() {
    setPushBusy(true);
    setPushError("");
    setPushMessage("");

    try {
      const registration = await navigator.serviceWorker.getRegistration("/");
      const subscription = await registration?.pushManager.getSubscription();

      if (subscription) {
        await notificationApi.unsubscribePush(subscription.endpoint);
        await subscription.unsubscribe();
      }

      setPushEnabledInBrowser(false);
      setPushMessage("이 브라우저의 Push 알림을 껐습니다.");
      await queryClient.invalidateQueries({
        queryKey: ["notifications", "push-status"],
      });
    } catch (error) {
      setPushError(errorMessage(error));
    } finally {
      setPushBusy(false);
    }
  }

  async function testPush() {
    setPushBusy(true);
    setPushError("");
    setPushMessage("");

    try {
      const result = await notificationApi.testPush();
      setPushMessage(
        result.successCount > 0
          ? `테스트 Push를 ${result.successCount}개 기기에 보냈습니다.`
          : "Push 전송을 시도했지만 성공한 기기가 없습니다.",
      );
    } catch (error) {
      setPushError(errorMessage(error));
    } finally {
      setPushBusy(false);
    }
  }

  const serverPushEnabled = pushConfigQuery.data?.enabled ?? false;

  return (
    <section className="app-card">
      <p className="card-label">알림 설정</p>
      <h2 className="mt-2 text-lg font-bold text-slate-950">
        합주 리마인더 · Push
      </h2>
      <p className="mt-2 text-sm leading-6 text-slate-500">
        합주 리마인더는 기본 30분 전입니다. Push는 브라우저별로 각각
        허용해야 합니다.
      </p>

      <div className="mt-5 grid gap-4 sm:grid-cols-[1fr_auto] sm:items-end">
        <label className="field-label">
          합주 리마인더
          <select
            className="field-input"
            value={reminderValue}
            disabled={settingsQuery.isPending || settingsMutation.isPending}
            onChange={(event) => {
              const value = event.target.value;
              settingsMutation.mutate(
                value === "off" ? null : Number(value),
              );
            }}
          >
            {REMINDER_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>

        <p className="pb-3 text-xs font-semibold text-slate-400">
          {settingsMutation.isPending ? "저장 중..." : "변경 즉시 저장"}
        </p>
      </div>

      {(settingsQuery.isError || settingsMutation.isError) && (
        <p className="error-box mt-3">
          {errorMessage(settingsQuery.error ?? settingsMutation.error)}
        </p>
      )}

      <div className="mt-6 border-t border-slate-100 pt-5">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="font-bold text-slate-950">이 브라우저 Push</p>
            <p className="mt-1 text-xs leading-5 text-slate-500">
              {!pushSupported
                ? "이 브라우저는 Web Push를 지원하지 않습니다."
                : !serverPushEnabled
                  ? "서버 VAPID 키를 설정하면 사용할 수 있습니다."
                  : pushPermission === "denied"
                    ? "브라우저 설정에서 알림 권한을 다시 허용해주세요."
                    : pushEnabledInBrowser
                      ? "현재 브라우저에서 Push를 받고 있습니다."
                      : "현재 브라우저의 Push가 꺼져 있습니다."}
            </p>
            {pushStatusQuery.data && (
              <p className="mt-1 text-[11px] text-slate-400">
                내 계정의 활성 Push 기기 {pushStatusQuery.data.activeSubscriptions}개
              </p>
            )}
          </div>

          <div className="flex flex-wrap gap-2">
            {pushEnabledInBrowser ? (
              <button
                type="button"
                className="secondary-button small-button"
                disabled={pushBusy || !pushSupported}
                onClick={() => void disablePush()}
              >
                Push 끄기
              </button>
            ) : (
              <button
                type="button"
                className="primary-button small-button"
                disabled={
                  pushBusy ||
                  !pushSupported ||
                  !serverPushEnabled ||
                  pushPermission === "denied"
                }
                onClick={() => void enablePush()}
              >
                Push 켜기
              </button>
            )}

            {pushEnabledInBrowser && serverPushEnabled && (
              <button
                type="button"
                className="secondary-button small-button"
                disabled={pushBusy}
                onClick={() => void testPush()}
              >
                테스트
              </button>
            )}
          </div>
        </div>

        {pushConfigQuery.isError && (
          <p className="error-box mt-3">
            {errorMessage(pushConfigQuery.error)}
          </p>
        )}
        {pushError && <p className="error-box mt-3">{pushError}</p>}
        {pushMessage && (
          <p className="mt-3 rounded-2xl bg-slate-50 px-4 py-3 text-xs font-semibold text-slate-600">
            {pushMessage}
          </p>
        )}
      </div>
    </section>
  );
}

function urlBase64ToUint8Array(base64String: string) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/-/g, "+")
    .replace(/_/g, "/");
  const rawData = window.atob(base64);

  return Uint8Array.from(
    Array.from(rawData, (character) => character.charCodeAt(0)),
  );
}
