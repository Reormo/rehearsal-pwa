"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import { scheduleAdminApi } from "@/lib/schedule-api";

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
  const [date, setDate] = useState(todayIso());
  const range = useMemo(() => monthRange(date), [date]);

  const effectiveQuery = useQuery({
    queryKey: ["admin", "operating-hours", date],
    queryFn: () => scheduleAdminApi.operatingHours(date),
  });
  const overridesQuery = useQuery({
    queryKey: ["admin", "operating-hours", "range", range.from, range.to],
    queryFn: () => scheduleAdminApi.operatingHourOverrides(range.from, range.to),
  });

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
    mutationFn: scheduleAdminApi.overrideOperatingHours,
    onSuccess: refresh,
  });
  const restoreMutation = useMutation({
    mutationFn: ({ date: targetDate, reason }: { date: string; reason: string }) =>
      scheduleAdminApi.restoreDefaultOperatingHours(targetDate, reason),
    onSuccess: refresh,
  });

  const error = overrideMutation.error ?? restoreMutation.error;
  const current = effectiveQuery.data;

  function restoreDefault() {
    const reason = window.prompt("기본 10:00~22:00으로 복원하는 사유를 입력하세요.")?.trim();
    if (!reason) return;
    restoreMutation.mutate({ date, reason });
  }

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN · ROOM HOURS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            날짜별 운영시간
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            기본값은 10:00~22:00이고 필요한 날짜만 Override합니다. 30분 경계로
            00:00~24:00까지 설정할 수 있으며, 운영시간을 줄여 범위 밖이 되는 활성 예약은
            자동 취소됩니다.
          </p>
        </div>
        <Link href="/admin/schedule" className="secondary-button">
          일정 설정으로
        </Link>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <section className="app-card">
        <label className="block max-w-xs">
          <span className="card-label">관리 날짜</span>
          <input
            className="field-input mt-2"
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
          />
        </label>

        {effectiveQuery.isPending && <p className="mt-5 text-sm text-slate-400">불러오는 중...</p>}
        {effectiveQuery.isError && (
          <p className="error-box mt-5">{errorMessage(effectiveQuery.error)}</p>
        )}
        {current && (
          <div className="mt-5 rounded-2xl bg-slate-50 p-4">
            <p className="text-sm font-bold text-slate-950">
              현재 {current.openTime} ~ {current.closeTime}
            </p>
            <p className="mt-1 text-xs text-slate-500">
              {current.overridden ? `Override · ${current.reason ?? "사유 없음"}` : "기본 운영시간"}
            </p>
          </div>
        )}

        <form
          className="mt-5 grid gap-3 sm:grid-cols-2"
          key={`${date}:${current?.openTime}:${current?.closeTime}`}
          onSubmit={(event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            overrideMutation.mutate({
              date,
              openTime: String(form.get("openTime")),
              closeTime: String(form.get("closeTime")),
              reason: String(form.get("reason")).trim(),
            });
          }}
        >
          <label>
            <span className="card-label">오픈</span>
            <select className="field-input mt-2" name="openTime" defaultValue={current?.openTime ?? "10:00"}>
              {BOUNDARIES.slice(0, -1).map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label>
            <span className="card-label">종료</span>
            <select className="field-input mt-2" name="closeTime" defaultValue={current?.closeTime ?? "22:00"}>
              {BOUNDARIES.slice(1).map((value) => (
                <option key={value} value={value}>{value}</option>
              ))}
            </select>
          </label>
          <label className="sm:col-span-2">
            <span className="card-label">변경 사유</span>
            <input className="field-input mt-2" name="reason" maxLength={500} required />
          </label>
          <div className="flex flex-wrap gap-2 sm:col-span-2">
            <button className="primary-button" type="submit" disabled={overrideMutation.isPending}>
              {overrideMutation.isPending ? "저장 중..." : "운영시간 Override"}
            </button>
            <button className="secondary-button" type="button" onClick={restoreDefault} disabled={restoreMutation.isPending}>
              기본 10:00~22:00 복원
            </button>
          </div>
        </form>
      </section>

      <section className="app-card">
        <p className="card-label">이번 달 Override</p>
        <div className="mt-4 space-y-2">
          {overridesQuery.isPending && <p className="text-sm text-slate-400">불러오는 중...</p>}
          {overridesQuery.isError && <p className="error-box">{errorMessage(overridesQuery.error)}</p>}
          {overridesQuery.data?.length === 0 && <p className="text-sm text-slate-500">등록된 Override가 없습니다.</p>}
          {overridesQuery.data?.map((item) => (
            <button
              key={item.date}
              type="button"
              className="flex w-full items-center justify-between rounded-2xl border border-slate-200 px-4 py-3 text-left"
              onClick={() => setDate(item.date)}
            >
              <span>
                <span className="block font-semibold text-slate-900">{item.date}</span>
                <span className="mt-1 block text-xs text-slate-500">{item.reason}</span>
              </span>
              <span className="count-badge">{item.openTime}~{item.closeTime}</span>
            </button>
          ))}
        </div>
      </section>
    </div>
  );
}

function todayIso() {
  const now = new Date();
  const formatter = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });
  return formatter.format(now);
}

function monthRange(date: string) {
  const [year, month] = date.split("-").map(Number);
  const from = `${year}-${String(month).padStart(2, "0")}-01`;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  const to = `${year}-${String(month).padStart(2, "0")}-${String(lastDay).padStart(2, "0")}`;
  return { from, to };
}
