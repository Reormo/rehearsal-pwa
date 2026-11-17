"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import { scheduleAdminApi } from "@/lib/schedule-api";

const WEEKDAYS = ["일", "월", "화", "수", "목", "금", "토"];
const BOUNDARIES = Array.from({ length: 49 }, (_, index) => {
  const minutes = index * 30;
  if (minutes === 1440) return "24:00";
  const hour = Math.floor(minutes / 60);
  const minute = minutes % 60;
  return `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
});

export default function AdminOperatingHoursPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <OperatingHoursContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function OperatingHoursContent() {
  const queryClient = useQueryClient();
  const today = useMemo(() => parseIsoDate(todayIso()), []);
  const [month, setMonth] = useState(
    () => new Date(today.getFullYear(), today.getMonth(), 1),
  );
  const [selectedDates, setSelectedDates] = useState<string[]>([]);
  const range = useMemo(() => calendarRange(month), [month]);

  const overridesQuery = useQuery({
    queryKey: ["admin", "operating-hours", "range", range.from, range.to],
    queryFn: () => scheduleAdminApi.operatingHourOverrides(range.from, range.to),
  });
  const overrideByDate = useMemo(
    () =>
      new Map(
        (overridesQuery.data ?? []).map((item) => [item.date, item] as const),
      ),
    [overridesQuery.data],
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "operating-hours"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "reservations"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["reservations", "mine"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
    ]);
  };

  const overrideMutation = useMutation({
    mutationFn: async ({
      dates,
      openTime,
      closeTime,
      reason,
    }: {
      dates: string[];
      openTime: string;
      closeTime: string;
      reason: string;
    }) =>
      Promise.all(
        dates.map((date) =>
          scheduleAdminApi.overrideOperatingHours({
            date,
            openTime,
            closeTime,
            reason,
          }),
        ),
      ),
    onSuccess: async () => {
      setSelectedDates([]);
      await refresh();
    },
  });

  const restoreMutation = useMutation({
    mutationFn: async ({
      dates,
      reason,
    }: {
      dates: string[];
      reason: string;
    }) =>
      Promise.all(
        dates.map((date) =>
          scheduleAdminApi.restoreDefaultOperatingHours(date, reason),
        ),
      ),
    onSuccess: async () => {
      setSelectedDates([]);
      await refresh();
    },
  });

  const error = overrideMutation.error ?? restoreMutation.error;

  function toggleDate(date: string) {
    setSelectedDates((current) =>
      current.includes(date)
        ? current.filter((item) => item !== date)
        : [...current, date].sort(),
    );
  }

  function moveMonth(delta: number) {
    setMonth(
      (current) =>
        new Date(current.getFullYear(), current.getMonth() + delta, 1),
    );
    setSelectedDates([]);
  }

  function restoreDefault() {
    if (selectedDates.length === 0) return;
    const reason = window
      .prompt(
        `선택한 ${selectedDates.length}일을 기본 10:00~22:00으로 복원하는 사유를 입력하세요.`,
      )
      ?.trim();
    if (!reason) return;
    restoreMutation.mutate({ dates: selectedDates, reason });
  }

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-slate-950">
            날짜별 운영시간
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            적용할 날짜를 달력에서 먼저 선택한 뒤 운영시간을 한 번에 설정합니다.
            기본값은 10:00~22:00이며 모든 시간은 30분 단위입니다.
          </p>
        </div>
        <Link href="/admin/schedule" className="secondary-button">
          일정 설정으로
        </Link>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <section className="app-card select-none">
        <div className="flex items-center justify-between gap-3">
          <button
            type="button"
            className="secondary-button small-button"
            aria-label="이전 달"
            onClick={() => moveMonth(-1)}
          >
            &lt;
          </button>
          <div className="text-center">
            <p className="card-label">날짜 선택</p>
            <h2 className="mt-1 text-lg font-bold text-slate-950">
              {month.getFullYear()}년 {month.getMonth() + 1}월
            </h2>
          </div>
          <button
            type="button"
            className="secondary-button small-button"
            aria-label="다음 달"
            onClick={() => moveMonth(1)}
          >
            &gt;
          </button>
        </div>

        <div className="mt-4 grid grid-cols-7 gap-1 text-center">
          {WEEKDAYS.map((weekday, index) => (
            <div
              key={weekday}
              className={`py-2 text-xs font-bold ${
                index === 0
                  ? "text-red-500"
                  : index === 6
                    ? "text-blue-500"
                    : "text-slate-400"
              }`}
            >
              {weekday}
            </div>
          ))}

          {range.dates.map((date) => {
            const iso = toIsoDate(date);
            const outside = date.getMonth() !== month.getMonth();
            const selected = selectedDates.includes(iso);
            const overridden = overrideByDate.has(iso);
            const weekday = date.getDay();

            return (
              <button
                key={iso}
                type="button"
                disabled={outside}
                aria-pressed={selected}
                onClick={() => toggleDate(iso)}
                className={`min-h-14 rounded-xl border px-1 py-2 text-center transition ${
                  selected
                    ? "border-slate-950 bg-slate-950 text-white"
                    : overridden
                      ? "border-blue-200 bg-blue-50"
                      : "border-slate-100 bg-white hover:border-slate-300"
                } ${outside ? "cursor-default opacity-20" : ""}`}
              >
                <span
                  className={`block text-sm font-bold ${
                    selected
                      ? "text-white"
                      : weekday === 0
                        ? "text-red-500"
                        : weekday === 6
                          ? "text-blue-500"
                          : "text-slate-800"
                  }`}
                >
                  {date.getDate()}
                </span>
                {overridden && !outside && (
                  <span
                    className={`mt-1 block text-[9px] font-bold ${
                      selected ? "text-white/75" : "text-blue-600"
                    }`}
                  >
                    별도
                  </span>
                )}
              </button>
            );
          })}
        </div>

        <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
          <span className="count-badge">선택 {selectedDates.length}일</span>
          {selectedDates.length > 0 && (
            <button
              type="button"
              className="text-xs font-bold text-slate-500 underline-offset-4 hover:text-slate-950 hover:underline"
              onClick={() => setSelectedDates([])}
            >
              선택 해제
            </button>
          )}
        </div>
      </section>

      <section className="app-card">
        <p className="card-label">운영시간 설정</p>
        {selectedDates.length === 0 ? (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-500">
            달력에서 운영시간을 변경할 날짜를 먼저 선택해주세요.
          </p>
        ) : (
          <form
            className="mt-4 grid gap-3 sm:grid-cols-2"
            onSubmit={(event: FormEvent<HTMLFormElement>) => {
              event.preventDefault();
              const form = new FormData(event.currentTarget);
              overrideMutation.mutate({
                dates: selectedDates,
                openTime: String(form.get("openTime")),
                closeTime: String(form.get("closeTime")),
                reason: String(form.get("reason")).trim(),
              });
            }}
          >
            <label>
              <span className="card-label">오픈</span>
              <select
                className="field-input mt-2"
                name="openTime"
                defaultValue="10:00"
              >
                {BOUNDARIES.slice(0, -1).map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <label>
              <span className="card-label">종료</span>
              <select
                className="field-input mt-2"
                name="closeTime"
                defaultValue="22:00"
              >
                {BOUNDARIES.slice(1).map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </label>
            <label className="sm:col-span-2">
              <span className="card-label">변경 사유</span>
              <input
                className="field-input mt-2"
                name="reason"
                maxLength={500}
                required
              />
            </label>
            <div className="flex flex-wrap gap-2 sm:col-span-2">
              <button
                className="primary-button"
                type="submit"
                disabled={overrideMutation.isPending}
              >
                선택 날짜에 적용
              </button>
              <button
                className="secondary-button"
                type="button"
                onClick={restoreDefault}
                disabled={restoreMutation.isPending}
              >
                기본 10:00~22:00 복원
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="app-card">
        <p className="card-label">이번 달 별도 운영시간</p>
        <div className="mt-4 space-y-2">
          {overridesQuery.isPending && (
            <p className="text-sm text-slate-400">불러오는 중...</p>
          )}
          {overridesQuery.isError && (
            <p className="error-box">{errorMessage(overridesQuery.error)}</p>
          )}
          {overridesQuery.data?.length === 0 && (
            <p className="text-sm text-slate-500">
              등록된 별도 운영시간이 없습니다.
            </p>
          )}
          {overridesQuery.data?.map((item) => (
            <button
              key={item.date}
              type="button"
              className="flex w-full items-center justify-between rounded-2xl border border-slate-200 px-4 py-3 text-left"
              onClick={() => toggleDate(item.date)}
            >
              <span>
                <span className="block font-semibold text-slate-900">
                  {item.date}
                </span>
                <span className="mt-1 block text-xs text-slate-500">
                  {item.reason}
                </span>
              </span>
              <span className="count-badge">
                {item.openTime}~{item.closeTime}
              </span>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

function todayIso() {
  const now = new Date();
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(now);
}

function parseIsoDate(value: string) {
  const [year, month, day] = value.split("-").map(Number);
  return new Date(year, month - 1, day);
}

function calendarRange(month: Date) {
  const first = new Date(month.getFullYear(), month.getMonth(), 1);
  const start = new Date(
    first.getFullYear(),
    first.getMonth(),
    first.getDate() - first.getDay(),
  );
  const dates = Array.from({ length: 42 }, (_, index) => {
    return new Date(
      start.getFullYear(),
      start.getMonth(),
      start.getDate() + index,
    );
  });
  const lastDay = new Date(
    month.getFullYear(),
    month.getMonth() + 1,
    0,
  ).getDate();

  return {
    from: `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, "0")}-01`,
    to: `${month.getFullYear()}-${String(month.getMonth() + 1).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}`,
    dates,
  };
}

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}
