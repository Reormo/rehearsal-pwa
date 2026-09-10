"use client";

import { useEffect, useRef, useState } from "react";

type PushBanner = {
  title: string;
  body: string;
  linkPath: string;
};

export function PwaRegistration() {
  const [pushBanner, setPushBanner] = useState<PushBanner | null>(null);
  const dismissTimerRef = useRef<number | null>(null);

  useEffect(() => {
    if (!("serviceWorker" in navigator)) {
      return;
    }

    void navigator.serviceWorker
      .register("/sw.js", {
        scope: "/",
        updateViaCache: "none",
      })
      .catch((error) => {
        console.error("PWA Service Worker registration failed.", error);
      });

    const onMessage = (event: MessageEvent) => {
      if (event.data?.type !== "MUHON_PUSH_RECEIVED") {
        return;
      }

      const payload = event.data.payload ?? {};
      setPushBanner({
        title: String(payload.title ?? "무혼 알림"),
        body: String(payload.body ?? "새 알림이 도착했습니다."),
        linkPath: String(payload.linkPath ?? "/notifications"),
      });

      if (dismissTimerRef.current != null) {
        window.clearTimeout(dismissTimerRef.current);
      }
      dismissTimerRef.current = window.setTimeout(() => {
        setPushBanner(null);
        dismissTimerRef.current = null;
      }, 7_000);
    };

    navigator.serviceWorker.addEventListener("message", onMessage);

    return () => {
      navigator.serviceWorker.removeEventListener("message", onMessage);
      if (dismissTimerRef.current != null) {
        window.clearTimeout(dismissTimerRef.current);
      }
    };
  }, []);

  if (!pushBanner) {
    return null;
  }

  return (
    <div
      className="fixed left-1/2 top-4 z-[100] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 rounded-2xl border border-slate-200 bg-white p-4 shadow-2xl"
      role="status"
      aria-live="polite"
    >
      <div className="flex items-start gap-3">
        <a href={pushBanner.linkPath} className="min-w-0 flex-1">
          <p className="text-sm font-black text-slate-950">{pushBanner.title}</p>
          <p className="mt-1 text-sm leading-5 text-slate-600">{pushBanner.body}</p>
        </a>
        <button
          type="button"
          className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full border border-slate-200 text-lg font-bold text-slate-400"
          aria-label="푸시 팝업 닫기"
          onClick={() => setPushBanner(null)}
        >
          ×
        </button>
      </div>
    </div>
  );
}
