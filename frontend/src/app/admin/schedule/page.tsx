"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { adminApi, errorMessage } from "@/lib/api";
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
  const songsQuery = useQuery({
    queryKey: ["admin", "songs"],
    queryFn: adminApi.songs,
  });
  const stageTypes = useMemo(
    () =>
      [
        ...new Map(
          (songsQuery.data ?? [])
            .filter((song) => song.status === "ACTIVE")
            .map((song) => [
              song.stageTypeId,
              { id: song.stageTypeId, name: song.stageTypeName },
            ]),
        ).values(),
      ].sort((a, b) => a.name.localeCompare(b.name, "ko-KR")),
    [songsQuery.data],
  );

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
      bookingCloseAt,
      maxReservationMinutes,
    }: {
      roundId: number;
      bookingOpenAt: string;
      bookingCloseAt: string;
      maxReservationMinutes: number;
    }) =>
      scheduleAdminApi.updateRound(roundId, {
        bookingOpenAt,
        bookingCloseAt,
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
            복수 예약 정책, 회차별 예약 기간, 무대별 우선 예약 기간과 동아리방 예외를 관리합니다.
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
        <p className="card-label">예약 정책</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          동일 팀 복수 예약
        </h2>
        <p className="mt-2 text-sm leading-6 text-slate-500">
          같은 팀이 한 회차에 예약을 여러 건 가질 수 있는지만 전역으로 관리합니다.
          회차의 예약 오픈·종료 시각과 최대 예약 시간은 아래 준비된 회차에서 직접
          설정합니다.
        </p>

        {settingsQuery.isPending && (
          <p className="mt-5 text-sm text-slate-400">불러오는 중...</p>
        )}
        {settingsQuery.isError && (
          <p className="error-box mt-5">{errorMessage(settingsQuery.error)}</p>
        )}
        {settingsQuery.data && (
          <form
            className="mt-5"
            onSubmit={(event) => {
              event.preventDefault();
              const form = new FormData(event.currentTarget);
              settingsMutation.mutate({
                allowMultipleReservations: form.get("allowMultiple") === "on",
                defaultBookingOpenLeadMinutes:
                  settingsQuery.data.defaultBookingOpenLeadMinutes,
                defaultMaxReservationMinutes:
                  settingsQuery.data.defaultMaxReservationMinutes,
              });
            }}
          >
            <label className="block rounded-2xl border border-slate-200 p-4">
              <span className="mt-1 flex items-center gap-3 text-sm font-semibold text-slate-700">
                <input
                  name="allowMultiple"
                  type="checkbox"
                  defaultChecked={
                    settingsQuery.data.allowMultipleReservations
                  }
                />
                동일 팀의 회차 내 복수 예약 허용
              </span>
            </label>

            <button
              className="primary-button mt-4"
              type="submit"
              disabled={settingsMutation.isPending}
            >
              {settingsMutation.isPending ? "저장 중..." : "예약 정책 저장"}
            </button>
          </form>
        )}
      </section>

      <section className="app-card">
        <p className="card-label">예약 회차</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">
          준비된 회차
        </h2>
        <p className="mt-2 text-sm text-slate-500">
          운영 화면에는 이번 회차와 다음 회차만 표시됩니다. 월요일 00:00이 되면
          지난 회차 운영 데이터는 영구 삭제되고 다음 주 회차가 자동으로 준비됩니다.
        </p>

        <div className="mt-5 space-y-3">
          {roundsQuery.isPending && <EmptyText>불러오는 중...</EmptyText>}
          {roundsQuery.isError && (
            <p className="error-box">{errorMessage(roundsQuery.error)}</p>
          )}
          {roundsQuery.data?.map((round, index) => (
            <RoundEditor
              key={round.id}
              round={round}
              roundLabel={index === 0 ? "이번 회차" : "다음 회차"}
              stageTypes={stageTypes}
              disabled={roundMutation.isPending}
              onSave={(
                bookingOpenAt,
                bookingCloseAt,
                maxReservationMinutes,
              ) =>
                roundMutation.mutate({
                  roundId: round.id,
                  bookingOpenAt,
                  bookingCloseAt,
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

type StageTypeSummary = {
  id: number;
  name: string;
};

function RoundEditor({
  round,
  roundLabel,
  stageTypes,
  disabled,
  onSave,
}: {
  round: BookingRound;
  roundLabel: "이번 회차" | "다음 회차";
  stageTypes: StageTypeSummary[];
  disabled: boolean;
  onSave: (
    bookingOpenAt: string,
    bookingCloseAt: string,
    maxReservationMinutes: number,
  ) => void;
}) {
  const queryClient = useQueryClient();

  const stageWindowsQuery = useQuery({
    queryKey: ["admin", "schedule", "stage-windows", round.id],
    queryFn: () => scheduleAdminApi.stageWindows(round.id),
  });

  const saveStageWindowMutation = useMutation({
    mutationFn: ({
      stageTypeId,
      bookingOpenAt,
      bookingCloseAt,
      maxReservationMinutes,
    }: {
      stageTypeId: number;
      bookingOpenAt: string;
      bookingCloseAt: string;
      maxReservationMinutes: number;
    }) =>
      scheduleAdminApi.updateStageWindow(round.id, stageTypeId, {
        bookingOpenAt,
        bookingCloseAt,
        maxReservationMinutes,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["admin", "schedule", "stage-windows", round.id],
        }),
        queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      ]);
    },
  });

  const deleteStageWindowMutation = useMutation({
    mutationFn: (stageTypeId: number) =>
      scheduleAdminApi.deleteStageWindow(round.id, stageTypeId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ["admin", "schedule", "stage-windows", round.id],
        }),
        queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      ]);
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const openValue = String(form.get("bookingOpenAt"));
    const closeValue = String(form.get("bookingCloseAt"));
    const bookingOpenAt = new Date(`${openValue}:00+09:00`).toISOString();
    const bookingCloseAt = new Date(`${closeValue}:00+09:00`).toISOString();

    onSave(
      bookingOpenAt,
      bookingCloseAt,
      Number(form.get("maxMinutes")),
    );
  }

  const stageWindowError =
    stageWindowsQuery.error ??
    saveStageWindowMutation.error ??
    deleteStageWindowMutation.error;

  return (
    <div className="rounded-2xl border border-slate-200 p-4">
      <form onSubmit={submit}>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="font-bold text-slate-950">{roundLabel}</p>
            <p className="mt-1 text-sm text-slate-500">
              {round.startDate} ~ {round.endDate}
            </p>
          </div>
          <span className="count-badge">{roundStateLabel(round.state)}</span>
        </div>

        <div className="mt-4 grid gap-3 lg:grid-cols-[1fr_1fr_160px_auto] lg:items-end">
          <label>
            <span className="card-label">기본 예약 오픈</span>
            <input
              className="field-input mt-2"
              type="datetime-local"
              name="bookingOpenAt"
              defaultValue={toKoreanDateTimeLocal(round.bookingOpenAt)}
              required
            />
          </label>

          <label>
            <span className="card-label">기본 예약 종료</span>
            <input
              className="field-input mt-2"
              type="datetime-local"
              name="bookingCloseAt"
              defaultValue={toKoreanDateTimeLocal(round.bookingCloseAt)}
              required
            />
          </label>

          <label>
            <span className="card-label">1회 최대 예약</span>
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
            기본 설정 저장
          </button>
        </div>
      </form>

      <div className="mt-6 border-t border-slate-200 pt-5">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <p className="card-label">무대 종류별 예약 정책</p>
            <p className="mt-1 text-sm leading-6 text-slate-500">
              현재 활성 곡에서 사용 중인 무대만 자동으로 나타납니다.
              예약 오픈·종료 시각과 1회 최대 예약 시간을 종류별로 따로 지정할 수 있고,
              별도 설정이 없으면 위 회차 기본 설정을 사용합니다.
            </p>
          </div>
          <span className="count-badge">{stageTypes.length}종류</span>
        </div>

        {stageWindowError && (
          <p className="error-box mt-4">{errorMessage(stageWindowError)}</p>
        )}

        {stageWindowsQuery.isPending && (
          <p className="mt-4 text-sm text-slate-400">
            무대별 예약 기간을 불러오는 중...
          </p>
        )}

        {!stageWindowsQuery.isPending && stageTypes.length === 0 && (
          <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-5 text-center text-sm text-slate-500">
            활성 곡에 지정된 무대 종류가 없습니다.
          </p>
        )}

        {stageTypes.length > 0 && (
          <div className="mt-4 space-y-3">
            {stageTypes.map((stageType) => {
              const customWindow = stageWindowsQuery.data?.find(
                (window) => window.stageTypeId === stageType.id,
              );

              return (
                <StageWindowEditor
                  key={`${stageType.id}-${customWindow?.bookingOpenAt ?? "default"}-${customWindow?.bookingCloseAt ?? "default"}`}
                  stageType={stageType}
                  round={round}
                  customWindow={customWindow}
                  disabled={
                    disabled ||
                    saveStageWindowMutation.isPending ||
                    deleteStageWindowMutation.isPending
                  }
                  onSave={(
                    bookingOpenAt,
                    bookingCloseAt,
                    maxReservationMinutes,
                  ) =>
                    saveStageWindowMutation.mutate({
                      stageTypeId: stageType.id,
                      bookingOpenAt,
                      bookingCloseAt,
                      maxReservationMinutes,
                    })
                  }
                  onUseDefault={() =>
                    deleteStageWindowMutation.mutate(stageType.id)
                  }
                />
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

function StageWindowEditor({
  stageType,
  round,
  customWindow,
  disabled,
  onSave,
  onUseDefault,
}: {
  stageType: StageTypeSummary;
  round: BookingRound;
  customWindow:
    | Awaited<ReturnType<typeof scheduleAdminApi.stageWindows>>[number]
    | undefined;
  disabled: boolean;
  onSave: (
    bookingOpenAt: string,
    bookingCloseAt: string,
    maxReservationMinutes: number,
  ) => void;
  onUseDefault: () => void;
}) {
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const openValue = String(form.get("stageBookingOpenAt"));
    const closeValue = String(form.get("stageBookingCloseAt"));

    onSave(
      new Date(`${openValue}:00+09:00`).toISOString(),
      new Date(`${closeValue}:00+09:00`).toISOString(),
      Number(form.get("stageMaxMinutes")),
    );
  }

  return (
    <form
      className="rounded-2xl bg-slate-50 p-4"
      onSubmit={submit}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <p className="font-bold text-slate-950">{stageType.name}</p>
        <span className="count-badge">
          {customWindow ? "별도 설정" : "기본 설정 사용"}
        </span>
      </div>

      <div className="mt-3 grid gap-3 lg:grid-cols-[1fr_1fr_160px_auto_auto] lg:items-end">
        <label>
          <span className="card-label">예약 오픈</span>
          <input
            className="field-input mt-2"
            type="datetime-local"
            name="stageBookingOpenAt"
            defaultValue={toKoreanDateTimeLocal(
              customWindow?.bookingOpenAt ?? round.bookingOpenAt,
            )}
            required
          />
        </label>

        <label>
          <span className="card-label">예약 종료</span>
          <input
            className="field-input mt-2"
            type="datetime-local"
            name="stageBookingCloseAt"
            defaultValue={toKoreanDateTimeLocal(
              customWindow?.bookingCloseAt ?? round.bookingCloseAt,
            )}
            required
          />
        </label>

        <label>
          <span className="card-label">1회 최대 예약</span>
          <select
            className="field-input mt-2"
            name="stageMaxMinutes"
            defaultValue={
              customWindow?.maxReservationMinutes ??
              round.maxReservationMinutes
            }
          >
            {MAX_MINUTES.map((minutes) => (
              <option key={minutes} value={minutes}>
                {minutes}분
              </option>
            ))}
          </select>
        </label>

        <button
          className="secondary-button"
          type="submit"
          disabled={disabled}
        >
          별도 설정 저장
        </button>

        {customWindow && (
          <button
            className="secondary-button"
            type="button"
            disabled={disabled}
            onClick={onUseDefault}
          >
            기본 설정 사용
          </button>
        )}
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
