"use client";

import { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { adminApi, errorMessage } from "@/lib/api";
import {
  Reservation,
  ReservationBoundary,
  scheduleAdminApi,
} from "@/lib/schedule-api";

const DURATIONS = [30, 60, 90, 120, 150, 180];

type AdminAction =
  | { kind: "move"; reservationId: number; startAt: string; reason: string }
  | { kind: "extend"; reservationId: number; boundary: ReservationBoundary; reason: string }
  | { kind: "shorten"; reservationId: number; boundary: ReservationBoundary; reason: string }
  | { kind: "cancel"; reservationId: number; reason: string };

export default function AdminReservationsPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminReservationsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminReservationsContent() {
  const queryClient = useQueryClient();
  const reservationsQuery = useQuery({
    queryKey: ["admin", "reservations"],
    queryFn: scheduleAdminApi.adminReservations,
  });
  const songsQuery = useQuery({
    queryKey: ["admin", "songs"],
    queryFn: adminApi.songs,
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "reservations"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["reservations", "mine"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
    ]);
  };

  const createMutation = useMutation({
    mutationFn: scheduleAdminApi.adminCreateReservation,
    onSuccess: refresh,
  });

  const actionMutation = useMutation({
    mutationFn: async (action: AdminAction) => {
      switch (action.kind) {
        case "move":
          return scheduleAdminApi.adminMoveReservation(
            action.reservationId,
            action.startAt,
            action.reason,
          );
        case "extend":
          return scheduleAdminApi.adminExtendReservation(
            action.reservationId,
            action.boundary,
            action.reason,
          );
        case "shorten":
          return scheduleAdminApi.adminShortenReservation(
            action.reservationId,
            action.boundary,
            action.reason,
          );
        case "cancel":
          return scheduleAdminApi.adminCancelReservation(
            action.reservationId,
            action.reason,
          );
      }
      throw new Error("지원하지 않는 관리자 예약 작업입니다.");
    },
    onSuccess: refresh,
  });

  const error = createMutation.error ?? actionMutation.error;
  const activeSongs = (songsQuery.data ?? []).filter((song) => song.status === "ACTIVE");

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN · RESERVATIONS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            예약 강제 관리
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            관리자는 예약 오픈 상태와 팀장 권한을 우회할 수 있지만 실제 운영시간,
            사용 불가 시간, 다른 예약 충돌과 현재 최대 예약 시간은 그대로 준수합니다.
          </p>
        </div>
        <Link href="/admin/schedule" className="secondary-button">
          일정 설정으로
        </Link>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <section className="app-card">
        <p className="card-label">직접 예약</p>
        <h2 className="mt-2 text-lg font-bold text-slate-950">관리자 예약 생성</h2>
        <form
          className="mt-5 grid gap-3 sm:grid-cols-2"
          onSubmit={(event) => {
            event.preventDefault();
            const form = new FormData(event.currentTarget);
            createMutation.mutate({
              songId: Number(form.get("songId")),
              startAt: koreanLocalToInstant(String(form.get("startAt"))),
              durationMinutes: Number(form.get("durationMinutes")),
              reason: String(form.get("reason")).trim(),
            });
          }}
        >
          <label>
            <span className="card-label">곡</span>
            <select className="field-input mt-2" name="songId" required>
              <option value="">선택</option>
              {activeSongs.map((song) => (
                <option key={song.id} value={song.id}>
                  {song.title}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="card-label">시작 시각</span>
            <input
              className="field-input mt-2"
              type="datetime-local"
              name="startAt"
              step={1800}
              required
            />
          </label>
          <label>
            <span className="card-label">예약 길이</span>
            <select className="field-input mt-2" name="durationMinutes" defaultValue={60}>
              {DURATIONS.map((minutes) => (
                <option key={minutes} value={minutes}>
                  {minutes}분
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="card-label">관리 사유</span>
            <input
              className="field-input mt-2"
              name="reason"
              maxLength={500}
              placeholder="예: 운영진 요청"
              required
            />
          </label>
          <div className="sm:col-span-2">
            <button className="primary-button" type="submit" disabled={createMutation.isPending}>
              {createMutation.isPending ? "생성 중..." : "관리자 예약 생성"}
            </button>
          </div>
        </form>
      </section>

      <section className="space-y-4">
        <div>
          <p className="card-label">예정 예약</p>
          <h2 className="mt-2 text-lg font-bold text-slate-950">전체 활성 예약</h2>
        </div>
        {reservationsQuery.isPending && <p className="app-card text-sm text-slate-400">불러오는 중...</p>}
        {reservationsQuery.isError && (
          <p className="error-box">{errorMessage(reservationsQuery.error)}</p>
        )}
        {reservationsQuery.data?.length === 0 && (
          <p className="app-card text-sm text-slate-500">예정된 예약이 없습니다.</p>
        )}
        {reservationsQuery.data?.map((reservation) => (
          <AdminReservationCard
            key={reservation.id}
            reservation={reservation}
            disabled={actionMutation.isPending}
            onAction={(action) => actionMutation.mutate(action)}
          />
        ))}
      </section>
    </div>
  );
}

function AdminReservationCard({
  reservation,
  disabled,
  onAction,
}: {
  reservation: Reservation;
  disabled: boolean;
  onAction: (action: AdminAction) => void;
}) {
  const duration = Math.round(
    (new Date(reservation.endAt).getTime() - new Date(reservation.startAt).getTime()) / 60000,
  );

  function reason(promptText: string) {
    const value = window.prompt(promptText)?.trim();
    return value ? value : null;
  }

  function adjust(kind: "extend" | "shorten", boundary: ReservationBoundary) {
    const value = reason(`${kind === "extend" ? "연장" : "단축"} 사유를 입력하세요.`);
    if (!value) return;
    onAction({ kind, reservationId: reservation.id, boundary, reason: value });
  }

  return (
    <article className="app-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="card-label">#{reservation.id} · {reservation.source}</p>
          <h3 className="mt-1 text-lg font-bold text-slate-950">{reservation.songTitle}</h3>
          <p className="mt-2 text-sm font-semibold text-slate-700">
            {formatKoreanDateTime(reservation.startAt)} ~ {formatTime(reservation.endAt)} · {duration}분
          </p>
        </div>
      </div>

      <form
        className="mt-5 flex flex-wrap gap-2 border-t border-slate-100 pt-5"
        onSubmit={(event: FormEvent<HTMLFormElement>) => {
          event.preventDefault();
          const form = new FormData(event.currentTarget);
          const moveReason = String(form.get("reason")).trim();
          onAction({
            kind: "move",
            reservationId: reservation.id,
            startAt: koreanLocalToInstant(String(form.get("startAt"))),
            reason: moveReason,
          });
        }}
      >
        <input
          className="field-input min-w-[220px] flex-1"
          type="datetime-local"
          name="startAt"
          step={1800}
          defaultValue={toKoreanDateTimeLocal(reservation.startAt)}
          required
        />
        <input
          className="field-input min-w-[180px] flex-1"
          name="reason"
          maxLength={500}
          placeholder="이동 사유"
          required
        />
        <button className="secondary-button" type="submit" disabled={disabled}>
          강제 이동
        </button>
      </form>

      <div className="mt-3 grid gap-2 sm:grid-cols-2">
        <button className="secondary-button" type="button" disabled={disabled} onClick={() => adjust("extend", "FRONT")}>
          앞쪽 30분 연장
        </button>
        <button className="secondary-button" type="button" disabled={disabled} onClick={() => adjust("extend", "BACK")}>
          뒤쪽 30분 연장
        </button>
        <button className="secondary-button" type="button" disabled={disabled} onClick={() => adjust("shorten", "FRONT")}>
          앞쪽 30분 단축
        </button>
        <button className="secondary-button" type="button" disabled={disabled} onClick={() => adjust("shorten", "BACK")}>
          뒤쪽 30분 단축
        </button>
      </div>
      <button
        className="danger-button mt-3 w-full"
        type="button"
        disabled={disabled}
        onClick={() => {
          const cancelReason = reason("강제 취소 사유를 입력하세요.");
          if (!cancelReason) return;
          if (window.confirm(`${reservation.songTitle} 예약을 강제 취소할까요?`)) {
            onAction({ kind: "cancel", reservationId: reservation.id, reason: cancelReason });
          }
        }}
      >
        강제 취소
      </button>
    </article>
  );
}

function koreanLocalToInstant(value: string) {
  return new Date(`${value}:00+09:00`).toISOString();
}

function toKoreanDateTimeLocal(value: string) {
  const parts = new Intl.DateTimeFormat("sv-SE", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(new Date(value));
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${values.year}-${values.month}-${values.day}T${values.hour}:${values.minute}`;
}

function formatKoreanDateTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "long",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(value));
}
