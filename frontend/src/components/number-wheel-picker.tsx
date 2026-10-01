"use client";

import { useEffect, useMemo, useRef, useState } from "react";

type NumberWheelPickerProps = {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  suffix?: string;
  disabled?: boolean;
};

const ITEM_HEIGHT = 48;
const VISIBLE_ITEMS = 5;
const SIDE_PADDING = ((VISIBLE_ITEMS - 1) / 2) * ITEM_HEIGHT;

export function NumberWheelPicker({
  value,
  onChange,
  min = 1,
  max = 99,
  suffix = "회",
  disabled = false,
}: NumberWheelPickerProps) {
  const values = useMemo(
    () => Array.from({ length: max - min + 1 }, (_, index) => min + index),
    [max, min],
  );
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState(value);
  const listRef = useRef<HTMLDivElement>(null);
  const frameRef = useRef<number | null>(null);

  useEffect(() => {
    if (!open) return;
    const index = Math.max(0, Math.min(values.length - 1, value - min));
    const timer = window.setTimeout(() => {
      listRef.current?.scrollTo({
        top: index * ITEM_HEIGHT,
        behavior: "auto",
      });
    }, 0);
    return () => window.clearTimeout(timer);
  }, [min, open, value, values.length]);

  useEffect(() => {
    return () => {
      if (frameRef.current !== null) {
        window.cancelAnimationFrame(frameRef.current);
      }
    };
  }, []);

  function handleScroll() {
    if (!listRef.current) return;
    if (frameRef.current !== null) {
      window.cancelAnimationFrame(frameRef.current);
    }
    frameRef.current = window.requestAnimationFrame(() => {
      if (!listRef.current) return;
      const index = Math.max(
        0,
        Math.min(
          values.length - 1,
          Math.round(listRef.current.scrollTop / ITEM_HEIGHT),
        ),
      );
      setDraft(values[index]);
    });
  }

  function choose(next: number) {
    const index = next - min;
    listRef.current?.scrollTo({
      top: index * ITEM_HEIGHT,
      behavior: "smooth",
    });
    setDraft(next);
  }

  return (
    <>
      <button
        type="button"
        className="field-input flex items-center justify-between text-left font-bold"
        disabled={disabled}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => {
          setDraft(value);
          setOpen(true);
        }}
      >
        <span>{value}{suffix}</span>
        <span className="text-slate-400" aria-hidden>⌄</span>
      </button>

      {open && (
        <div
          className="fixed inset-0 z-50 flex items-end justify-center bg-slate-950/35 p-0 sm:items-center sm:p-6"
          role="presentation"
          onMouseDown={(event) => {
            if (event.currentTarget === event.target) setOpen(false);
          }}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-label="최대 예약 횟수 선택"
            className="w-full rounded-t-3xl bg-white p-5 shadow-2xl sm:max-w-sm sm:rounded-3xl"
          >
            <div className="flex items-center justify-between gap-3">
              <div>
                <p className="card-label">회차 내 최대 예약</p>
                <p className="mt-1 text-lg font-bold text-slate-950">
                  {draft}{suffix}
                </p>
              </div>
              <button
                type="button"
                className="secondary-button small-button"
                onClick={() => setOpen(false)}
              >
                닫기
              </button>
            </div>

            <div className="relative mt-4 overflow-hidden rounded-2xl border border-slate-200 bg-slate-50">
              <div
                className="pointer-events-none absolute inset-x-3 top-1/2 z-10 h-12 -translate-y-1/2 rounded-xl border border-slate-300 bg-white/80"
                aria-hidden
              />
              <div
                ref={listRef}
                onScroll={handleScroll}
                className="relative z-20 h-60 snap-y snap-mandatory overflow-y-auto overscroll-contain [&::-webkit-scrollbar]:hidden"
                style={{
                  paddingTop: SIDE_PADDING,
                  paddingBottom: SIDE_PADDING,
                  scrollbarWidth: "none",
                }}
              >
                {values.map((option) => (
                  <button
                    key={option}
                    type="button"
                    className={`flex h-12 w-full snap-center items-center justify-center text-lg transition ${
                      option === draft
                        ? "font-extrabold text-slate-950"
                        : "font-semibold text-slate-400"
                    }`}
                    onClick={() => choose(option)}
                  >
                    {option}{suffix}
                  </button>
                ))}
              </div>
            </div>

            <div className="mt-5 grid grid-cols-2 gap-2">
              <button
                type="button"
                className="secondary-button"
                onClick={() => setOpen(false)}
              >
                취소
              </button>
              <button
                type="button"
                className="primary-button"
                onClick={() => {
                  onChange(draft);
                  setOpen(false);
                }}
              >
                완료
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
