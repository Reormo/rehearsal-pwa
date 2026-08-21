"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, songApi } from "@/lib/api";
import {
  Reservation,
  ReservationBoundary,
  scheduleApi,
} from "@/lib/schedule-api";

export default function MyReservationsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <ReservationsContent userId={user.id} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function ReservationsContent({ userId }: { userId: number }) {
  const queryClient = useQueryClient();
  const reservationsQuery = useQuery({
    queryKey: ["reservations", "mine"],
    queryFn: scheduleApi.myReservations,
  });
  const songsQuery = useQuery({
    queryKey: ["songs", "mine"],
    queryFn: songApi.mine,
  });

  const leaderSongIds = useMemo(
    () =>
      new Set(
        (songsQuery.data ?? [])
          .filter((song) =>
            song.members.some((member) => member.userId === userId && member.leader),
          )
          .map((song) => song.id),
      ),
    [songsQuery.data, userId],
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["reservations", "mine"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    ]);
  };

  const moveMutation = useMutation({
    mutationFn: ({ reservationId, startAt }: { reservationId: number; startAt: string }) =>
      scheduleApi.moveReservation(reservationId, startAt),
    onSuccess: refresh,
  });
  const extendMutation = useMutation({
    mutationFn: ({ reservationId, boundary }: { reservationId: number; boundary: ReservationBoundary }) =>
      scheduleApi.extendReservation(reservationId, boundary),
    onSuccess: refresh,
  });
  const shortenMutation = useMutation({
    mutationFn: ({ reservationId, boundary }: { reservationId: number; boundary: ReservationBoundary }) =>
      scheduleApi.shortenReservation(reservationId, boundary),
    onSuccess: refresh,
  });
  const cancelMutation = useMutation({
    mutationFn: scheduleApi.cancelReservation,
    onSuccess: refresh,
  });

  const mutationError =
    moveMutation.error ??
    extendMutation.error ??
    shortenMutation.error ??
    cancelMutation.error;
  const disabled =
    moveMutation.isPending ||
    extendMutation.isPending ||
    shortenMutation.isPending ||
    cancelMutation.isPending;

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">MY · REHEARSALS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            예정된 합주
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            참여 중인 곡의 예정 예약을 확인합니다. 수정과 취소는 해당 곡 팀장만 가능하고,
            합주가 시작된 뒤에는 관리자만 조정할 수 있습니다.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Link href="/my/swaps" className="primary-button">
            일정 교환
          </Link>
          <Link href="/schedule" className="secondary-button">
            전체 시간표 보기
          </Link>
        </div>
      </section>

      {mutationError && <p className="error-box">{errorMessage(mutationError)}</p>}
      {reservationsQuery.isPending && <p className="app-card text-sm text-slate-400">불러오는 중...</p>}
      {reservationsQuery.isError && (
        <p className="error-box">{errorMessage(reservationsQuery.error)}</p>
      )}
      {reservationsQuery.data?.length === 0 && (
        <p className="app-card text-sm text-slate-500">예정된 합주가 없습니다.</p>
      )}

      <div className="space-y-4">
        {reservationsQuery.data?.map((reservation) => (
          <ReservationCard
            key={reservation.id}
            reservation={reservation}
            editable={leaderSongIds.has(reservation.songId)}
            disabled={disabled}
            onMove={(startAt) => moveMutation.mutate({ reservationId: reservation.id, startAt })}
            onExtend={(boundary) => extendMutation.mutate({ reservationId: reservation.id, boundary })}
            onShorten={(boundary) => shortenMutation.mutate({ reservationId: reservation.id, boundary })}
            onCancel={() => {
              if (window.confirm(`${reservation.songTitle} 예약을 취소할까요?`)) {
                cancelMutation.mutate(reservation.id);
              }
            }}
          />
        ))}
      </div>
    </div>
  );
}

function ReservationCard({
  reservation,
  editable,
  disabled,
  onMove,
  onExtend,
  onShorten,
  onCancel,
}: {
  reservation: Reservation;
  editable: boolean;
  disabled: boolean;
  onMove: (startAt: string) => void;
  onExtend: (boundary: ReservationBoundary) => void;
  onShorten: (boundary: ReservationBoundary) => void;
  onCancel: () => void;
}) {
  const [moveValue, setMoveValue] = useState(toKoreanDateTimeLocal(reservation.startAt));
  const duration = Math.round(
    (new Date(reservation.endAt).getTime() - new Date(reservation.startAt).getTime()) / 60000,
  );

  function submitMove(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onMove(koreanLocalToInstant(moveValue));
  }

  return (
    <article className="app-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="card-label">{reservation.source === "ADMIN" ? "관리자 예약" : "팀 예약"}</p>
          <h2 className="mt-1 text-lg font-bold text-slate-950">{reservation.songTitle}</h2>
          <p className="mt-2 text-sm font-semibold text-slate-700">
            {formatKoreanDateTime(reservation.startAt)} ~ {formatTime(reservation.endAt)} · {duration}분
          </p>
        </div>
        <span className="count-badge">#{reservation.id}</span>
      </div>

      {!editable ? (
        <p className="mt-4 rounded-2xl bg-slate-50 px-4 py-3 text-sm text-slate-500">
          이 예약의 수정/취소는 현재 팀장 또는 관리자가 할 수 있습니다.
        </p>
      ) : (
        <div className="mt-5 space-y-4 border-t border-slate-100 pt-5">
          <form className="flex flex-wrap gap-2" onSubmit={submitMove}>
            <input
              className="field-input min-w-[220px] flex-1"
              type="datetime-local"
              step={1800}
              value={moveValue}
              onChange={(event) => setMoveValue(event.target.value)}
              required
            />
            <button className="secondary-button" type="submit" disabled={disabled}>
              시간 이동
            </button>
          </form>

          <div className="grid gap-2 sm:grid-cols-2">
            <button className="secondary-button" type="button" disabled={disabled} onClick={() => onExtend("FRONT")}>
              앞쪽 30분 연장
            </button>
            <button className="secondary-button" type="button" disabled={disabled} onClick={() => onExtend("BACK")}>
              뒤쪽 30분 연장
            </button>
            <button className="secondary-button" type="button" disabled={disabled} onClick={() => onShorten("FRONT")}>
              앞쪽 30분 단축
            </button>
            <button className="secondary-button" type="button" disabled={disabled} onClick={() => onShorten("BACK")}>
              뒤쪽 30분 단축
            </button>
          </div>
          <button className="danger-button w-full" type="button" disabled={disabled} onClick={onCancel}>
            예약 취소
          </button>
        </div>
      )}
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
