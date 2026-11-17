"use client";

import { useEffect, useRef } from "react";
import { createPortal } from "react-dom";
import { RehearsalCalendar } from "@/components/rehearsal-calendar";

export function RehearsalScheduleModal({ onClose }: { onClose: () => void }) {
  const panelRef = useRef<HTMLElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement
      ? document.activeElement : null;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeButtonRef.current?.focus();

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }
      if (event.key !== "Tab") return;
      const controls = panelRef.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), a[href], input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])',
      );
      if (!controls?.length) return;
      const first = controls[0];
      const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      document.body.style.overflow = previousOverflow;
      previousFocus?.focus();
    };
  }, [onClose]);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/60 px-3 py-4 sm:p-6"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose();
      }}
    >
      <section
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby="rehearsal-schedule-title"
        className="h-[calc(100dvh-2rem)] max-h-[720px] w-full max-w-xl overscroll-contain overflow-y-auto rounded-3xl bg-white p-4 shadow-2xl [scrollbar-gutter:stable] sm:p-6"
      >
        <div className="sticky top-0 z-10 mb-3 flex items-center justify-between gap-3 border-b border-slate-100 bg-white pb-3">
          <h2 id="rehearsal-schedule-title" className="text-xl font-black text-slate-950">
            전체 합주 일정
          </h2>
          <button
            type="button"
            ref={closeButtonRef}
            onClick={onClose}
            aria-label="전체 합주 일정 닫기"
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-slate-100 text-2xl text-slate-700 hover:bg-slate-200 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-slate-900"
          >
            ×
          </button>
        </div>
        <RehearsalCalendar embedded />
      </section>
    </div>,
    document.body,
  );
}
