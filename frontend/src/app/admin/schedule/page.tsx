"use client";

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import {
  BookingRound,
  RoomException,
  scheduleAdminApi,
} from "@/lib/schedule-api";

const MAX_MINUTES = [30, 60, 90, 120, 150, 180];

export default function AdminSchedulePage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminScheduleContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminScheduleContent() {
  const queryClient = useQueryClient();
  const [allDayBlocked, setAllDayBlocked] = useState(false);

  const exceptionRange = dateRange();
  const settingsQuery = useQuery({
    queryKey: ["admin", "schedule", "settings"],
    queryFn: scheduleAdminApi.settings,
  });
  const roundsQuery = useQuery({
    queryKey: ["admin", "schedule", "rounds"],
    queryFn: scheduleAdminApi.rounds,
  });
  const exceptionsQuery = useQuery({
    queryKey: [
      "admin",
      "schedule",
      "exceptions",
      exceptionRange.from,
      exceptionRange.to,
    ],
    queryFn: () =>
      scheduleAdminApi.exceptions(exceptionRange.from, exceptionRange.to),
  });

  const refreshSchedule = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
    ]);
  };

  const settingsMutation = useMutation({
    mutationFn: scheduleAdminApi.updateSettings,
    onSuccess: refreshSchedule,
  });

  const roundMutation = useMutation({
    mutationFn: ({
      roundId,
      bookingOpenAt,
      maxReservationMinutes,
    }: {
      roundId: number;
      bookingOpenAt: string;
      maxReservationMinutes: number;
    }) =>
      scheduleAdminApi.updateRound(roundId, {
        bookingOpenAt,
        maxReservationMinutes,
      }),
    onSuccess: refreshSchedule,
  });

  const exceptionMutation = useMutation({
    mutationFn: scheduleAdminApi.createException,
    onSuccess: refreshSchedule,
  });

  const deleteExceptionMutation = useMutation({
    mutationFn: scheduleAdminApi.deleteException,
    onSuccess: refreshSchedule,
  });

  const mutationError =
    settingsMutation.error ??
    roundMutation.error ??
    exceptionMutation.error ??
    deleteExceptionMutation.error;

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN · SCHEDULE</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            합주 운영 설정
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            기본 예약 정책, 회차별 오픈 시각과 동아리방 사용 불가 시간을 관리합니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href="/admin/reservations" className="secondary-button">
            예약 강제 관리
          </Link>
          <Link href="/admin/operating-hours" className="secondary-button">
            날짜별 운영시간
          </Link>
          <Link href="/schedule" className="secondary-button">
            전체 시간표 보기
          </Link>
        </div>
      </section>

      {mutationError && (
        <p className="error-box">{errorMessage(mutationError)}</p>
      )}

      <section className="app-card">
        <p className="card-label">기본 예약 정책</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          다음 회차 기본값
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          변경한 값은 이후 새로 준비되는 회차가 상속합니다. 이미 준비된 회차는 아래에서
          별도로 수정합니다.
        </p>

        {settingsQuery.isPending && (
          <p className="mt-5 text-sm text-slate-400">불러오는 중...</p>
        )}
        {settingsQuery.isError && (
          <p className="error-box mt-5">{errorMessage(settingsQuery.error)}</p>
        )}
        {settingsQuery.data && (
          <form
            className="mt-5 grid gap-4 sm:grid-cols-3"
            onSubmit={(event) => {
              event.preventDefault();
              const form = new FormData(event.currentTarget);
              const leadHours = Number(form.get("leadHours"));
              settingsMutation.mutate({
                allowMultipleReservations: form.get("allowMultiple") === "on",
                defaultBookingOpenLeadMinutes: Math.round(leadHours * 60),
                defaultMaxReservationMinutes: Number(form.get("maxMinutes")),
              });
            }}
          >
            <label className="rounded-2xl border border-slate-200 p-4">
              <span className="card-label">동일 팀 복수 예약</span>
              <span className="mt-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
                <input
                  name="allowMultiple"
                  type="checkbox"
                  defaultChecked={settingsQuery.data.allowMultipleReservations}
                />
                허용
              </span>
            </label>

            <label>
              <span className="card-label">기본 예약 오픈</span>
              <span className="mt-2 flex items-center gap-2">
                <input
                  className="field-input"
                  name="leadHours"
                  type="number"
                  min={0}
                  max={168}
                  step={0.5}
                  defaultValue={
                    settingsQuery.data.defaultBookingOpenLeadMinutes / 60
                  }
                  required
                />
                <span className="shrink-0 text-sm text-slate-500">
                  시간 전
                </span>
              </span>
              <span className="mt-1 block text-xs text-slate-400">
                기본 28시간 전 = 토요일 20:00
              </span>
            </label>

            <label>
              <span className="card-label">1회 최대 예약</span>
              <select
                className="field-input mt-2"
                name="maxMinutes"
                defaultValue={settingsQuery.data.defaultMaxReservationMinutes}
              >
                {MAX_MINUTES.map((minutes) => (
                  <option key={minutes} value={minutes}>
                    {minutes}분
                  </option>
                ))}
              </select>
              <span className="mt-1 block text-xs leading-5 text-slate-400">
                일반 예약 슬롯은 이 길이로 묶고, 남는 짧은 구간은 잔여 슬롯으로 제공합니다.
              </span>
            </label>

            <div className="sm:col-span-3">
              <button
                className="primary-button"
                type="submit"
                disabled={settingsMutation.isPending}
              >
                {settingsMutation.isPending ? "저장 중..." : "기본 정책 저장"}
              </button>
            </div>
          </form>
        )}
      </section>

      <section className="app-card">
        <p className="card-label">예약 회차</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          준비된 회차
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          현재 회차와 다음 회차는 자동으로 준비됩니다. DB에는 회차당 336개의 30분
          원자 슬롯을 유지하고, 화면의 예약 가능 슬롯만 최대 예약 시간에 맞춰 묶습니다.
        </p>

        <div className="mt-5 space-y-3">
          {roundsQuery.isPending && <EmptyText>불러오는 중...</EmptyText>}
          {roundsQuery.isError && (
            <p className="error-box">{errorMessage(roundsQuery.error)}</p>
          )}
          {roundsQuery.data?.map((round) => (
            <RoundEditor
              key={round.id}
              round={round}
              disabled={roundMutation.isPending}
              onSave={(bookingOpenAt, maxReservationMinutes) =>
                roundMutation.mutate({
                  roundId: round.id,
                  bookingOpenAt,
                  maxReservationMinutes,
                })
              }
            />
          ))}
        </div>
      </section>

      <section className="app-card">
        <p className="card-label">동아리방 예외</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          사용 불가 시간 추가
        </h2>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          기본 운영시간은 10:00~22:00이며 날짜별 Override가 있으면 해당 시간이 적용됩니다. 사용할 수 있는 시간을 따로 지정하지
          않고, 사용할 수 없는 구간만 등록합니다. 같은 날짜에 여러 구간을 추가할 수 있고
          나머지 시간은 자동으로 예약 가능합니다.
        </p>

        <form
          className="mt-5 grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            exceptionMutation.mutate({
              date: String(form.get("date")),
              blockedStartTime: allDayBlocked
                ? "10:00"
                : String(form.get("blockedStartTime")),
              blockedEndTime: allDayBlocked
                ? "22:00"
                : String(form.get("blockedEndTime")),
              reason: String(form.get("reason")).trim(),
            });
          }}
        >
          <label>
            <span className="card-label">날짜</span>
            <input
              className="field-input mt-2"
              type="date"
              name="date"
              defaultValue={todayIso()}
              required
            />
          </label>

          <label className="rounded-2xl border border-slate-200 p-4">
            <span className="card-label">전체 시간</span>
            <span className="mt-3 flex items-center gap-2 text-sm font-semibold text-slate-700">
              <input
                type="checkbox"
                checked={allDayBlocked}
                onChange={(event) => setAllDayBlocked(event.target.checked)}
              />
              10:00~22:00 전체 사용 불가
            </span>
          </label>

          <label>
            <span className="card-label">사용 불가 시작</span>
            <input
              className="field-input mt-2"
              type="time"
              name="blockedStartTime"
              min="10:00"
              max="21:30"
              step={1800}
              defaultValue="13:00"
              disabled={allDayBlocked}
              required={!allDayBlocked}
            />
          </label>
          <label>
            <span className="card-label">사용 불가 종료</span>
            <input
              className="field-input mt-2"
              type="time"
              name="blockedEndTime"
              min="10:30"
              max="22:00"
              step={1800}
              defaultValue="14:00"
              disabled={allDayBlocked}
              required={!allDayBlocked}
            />
          </label>

          <label className="sm:col-span-2">
            <span className="card-label">사유</span>
            <input
              className="field-input mt-2"
              name="reason"
              maxLength={500}
              placeholder="예: 수업 / 장비 점검 / 학교 행사"
              required
            />
          </label>
          <div className="sm:col-span-2">
            <button
              type="submit"
              className="primary-button"
              disabled={exceptionMutation.isPending}
            >
              {exceptionMutation.isPending ? "저장 중..." : "사용 불가 시간 추가"}
            </button>
          </div>
        </form>

        <div className="mt-6 space-y-3">
          {exceptionsQuery.isPending && <EmptyText>불러오는 중...</EmptyText>}
          {exceptionsQuery.isError && (
            <p className="error-box">{errorMessage(exceptionsQuery.error)}</p>
          )}
          {exceptionsQuery.data?.length === 0 && (
            <EmptyText>등록된 사용 불가 시간이 없습니다.</EmptyText>
          )}
          {exceptionsQuery.data?.map((exception) => (
            <ExceptionRow
              key={exception.id}
              exception={exception}
              disabled={deleteExceptionMutation.isPending}
              onDelete={() => deleteExceptionMutation.mutate(exception.id)}
            />
          ))}
        </div>
      </section>
    </div>
  );
}

function RoundEditor({
  round,
  disabled,
  onSave,
}: {
  round: BookingRound;
  disabled: boolean;
  onSave: (bookingOpenAt: string, maxReservationMinutes: number) => void;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const localValue = String(form.get("bookingOpenAt"));
    const instant = new Date(`${localValue}:00+09:00`).toISOString();
    onSave(instant, Number(form.get("maxMinutes")));
  }

  return (
    <form
      onSubmit={submit}
      className="rounded-2xl border border-slate-200 p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-bold text-slate-950">{round.roundNo}회차</p>
          <p className="mt-1 text-sm text-slate-500">
            {round.startDate} ~ {round.endDate}
          </p>
        </div>
        <span className="count-badge">{roundStateLabel(round.state)}</span>
      </div>

      <div className="mt-4 grid gap-3 sm:grid-cols-[1fr_160px_auto] sm:items-end">
        <label>
          <span className="card-label">예약 오픈 시각</span>
          <input
            className="field-input mt-2"
            type="datetime-local"
            name="bookingOpenAt"
            defaultValue={toKoreanDateTimeLocal(round.bookingOpenAt)}
            required
          />
        </label>
        <label>
          <span className="card-label">최대 예약</span>
          <select
            className="field-input mt-2"
            name="maxMinutes"
            defaultValue={round.maxReservationMinutes}
          >
            {MAX_MINUTES.map((minutes) => (
              <option key={minutes} value={minutes}>
                {minutes}분
              </option>
            ))}
          </select>
        </label>
        <button
          type="submit"
          className="secondary-button"
          disabled={disabled}
        >
          저장
        </button>
      </div>
    </form>
  );
}

function ExceptionRow({
  exception,
  disabled,
  onDelete,
}: {
  exception: RoomException;
  disabled: boolean;
  onDelete: () => void;
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-4 rounded-2xl border border-slate-200 p-4">
      <div>
        <p className="font-bold text-slate-950">{exception.date}</p>
        <p className="mt-1 text-sm font-semibold text-red-600">
          {trimTime(exception.blockedStartTime)} ~ {trimTime(exception.blockedEndTime)} 사용 불가
        </p>
        <p className="mt-1 text-sm text-slate-600">{exception.reason}</p>
      </div>
      <button
        type="button"
        className="danger-button small-button"
        disabled={disabled}
        onClick={() => {
          if (
            window.confirm(
              `${exception.date} ${trimTime(exception.blockedStartTime)}~${trimTime(exception.blockedEndTime)} 사용 불가 설정을 삭제할까요?`,
            )
          ) {
            onDelete();
          }
        }}
      >
        삭제
      </button>
    </div>
  );
}

function dateRange() {
  const today = new Date();
  const from = new Date(today.getFullYear(), today.getMonth() - 1, today.getDate());
  const to = new Date(today.getFullYear(), today.getMonth() + 4, today.getDate());
  return { from: toIsoDate(from), to: toIsoDate(to) };
}

function todayIso() {
  return toIsoDate(new Date());
}

function toIsoDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function toKoreanDateTimeLocal(value: string) {
  const koreanWallTime = new Date(new Date(value).getTime() + 9 * 60 * 60 * 1000);
  return koreanWallTime.toISOString().slice(0, 16);
}

function trimTime(value: string) {
  return value.slice(0, 5);
}

function roundStateLabel(state: BookingRound["state"]) {
  if (state === "UPCOMING") return "오픈 전";
  if (state === "BOOKING_OPEN") return "예약 접수 중";
  if (state === "IN_PROGRESS") return "진행 중";
  return "마감";
}

function EmptyText({ children }: { children: React.ReactNode }) {
  return (
    <p className="rounded-2xl bg-slate-50 px-4 py-6 text-center text-sm text-slate-400">
      {children}
    </p>
  );
}
