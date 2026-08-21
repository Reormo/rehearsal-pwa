"use client";

import { FormEvent, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage, songApi } from "@/lib/api";
import { scheduleApi } from "@/lib/schedule-api";
import { SwapRequest, swapApi } from "@/lib/swap-api";

export default function MySwapsPage() {
  return (
    <AuthGate>
      {(user) => (
        <AppShell user={user}>
          <MySwapsContent userId={user.id} />
        </AppShell>
      )}
    </AuthGate>
  );
}

function MySwapsContent({ userId }: { userId: number }) {
  const queryClient = useQueryClient();
  const [requesterReservationId, setRequesterReservationId] = useState<number | null>(null);

  const reservationsQuery = useQuery({
    queryKey: ["reservations", "mine"],
    queryFn: scheduleApi.myReservations,
  });
  const songsQuery = useQuery({
    queryKey: ["songs", "mine"],
    queryFn: songApi.mine,
  });
  const swapsQuery = useQuery({
    queryKey: ["swaps", "mine"],
    queryFn: swapApi.mine,
  });
  const candidatesQuery = useQuery({
    queryKey: ["swaps", "candidates", requesterReservationId],
    queryFn: () => swapApi.candidates(requesterReservationId as number),
    enabled: requesterReservationId !== null,
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
  const leaderReservations = (reservationsQuery.data ?? []).filter((reservation) =>
    leaderSongIds.has(reservation.songId),
  );

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["swaps"] }),
      queryClient.invalidateQueries({ queryKey: ["reservations", "mine"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications", "unread-count"] }),
    ]);
  };

  const createMutation = useMutation({
    mutationFn: ({ requesterId, targetId }: { requesterId: number; targetId: number }) =>
      swapApi.create(requesterId, targetId),
    onSuccess: refresh,
  });
  const actionMutation = useMutation({
    mutationFn: ({ kind, id }: { kind: "accept" | "reject" | "cancel"; id: number }) => {
      if (kind === "accept") return swapApi.accept(id);
      if (kind === "reject") return swapApi.reject(id);
      return swapApi.cancel(id);
    },
    onSuccess: refresh,
  });

  const error =
    createMutation.error ??
    actionMutation.error ??
    candidatesQuery.error ??
    swapsQuery.error;

  function submitRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const requesterId = Number(form.get("requesterReservationId"));
    const targetId = Number(form.get("targetReservationId"));
    createMutation.mutate({ requesterId, targetId });
  }

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">MY · SWAPS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            일정 교환
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            내가 팀장인 예약과 다른 팀의 예약을 교환 요청할 수 있습니다. 수락 시 각 팀은
            원래 예약 길이를 유지한 채 한 번의 트랜잭션으로 자리를 바꿉니다.
          </p>
        </div>
        <Link href="/my/reservations" className="secondary-button">
          예정 합주로
        </Link>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <section className="app-card">
        <p className="card-label">새 교환 요청</p>
        <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={submitRequest}>
          <label>
            <span className="card-label">내 팀 예약</span>
            <select
              className="field-input mt-2"
              name="requesterReservationId"
              value={requesterReservationId ?? ""}
              onChange={(event) => {
                const value = event.target.value;
                setRequesterReservationId(value ? Number(value) : null);
              }}
              required
            >
              <option value="">선택</option>
              {leaderReservations.map((reservation) => (
                <option key={reservation.id} value={reservation.id}>
                  {reservation.songTitle} · {formatRange(reservation.startAt, reservation.endAt)}
                </option>
              ))}
            </select>
          </label>

          <label>
            <span className="card-label">교환할 상대 예약</span>
            <select
              className="field-input mt-2"
              name="targetReservationId"
              disabled={!requesterReservationId || candidatesQuery.isPending}
              required
            >
              <option value="">선택</option>
              {candidatesQuery.data?.map((candidate) => (
                <option key={candidate.reservationId} value={candidate.reservationId}>
                  {candidate.songTitle} · {formatRange(candidate.startAt, candidate.endAt)}
                </option>
              ))}
            </select>
          </label>

          <div className="sm:col-span-2">
            <button
              className="primary-button"
              type="submit"
              disabled={createMutation.isPending || !requesterReservationId}
            >
              {createMutation.isPending ? "요청 중..." : "교환 요청 보내기"}
            </button>
          </div>
        </form>
      </section>

      <section className="space-y-3">
        <div>
          <p className="card-label">교환 요청 내역</p>
          <h2 className="mt-2 text-lg font-bold text-slate-950">내 팀의 요청</h2>
        </div>
        {swapsQuery.isPending && <p className="app-card text-sm text-slate-400">불러오는 중...</p>}
        {swapsQuery.data?.length === 0 && (
          <p className="app-card text-sm text-slate-500">교환 요청 내역이 없습니다.</p>
        )}
        {swapsQuery.data?.map((swap) => (
          <SwapCard
            key={swap.id}
            swap={swap}
            disabled={actionMutation.isPending}
            onAccept={() => actionMutation.mutate({ kind: "accept", id: swap.id })}
            onReject={() => actionMutation.mutate({ kind: "reject", id: swap.id })}
            onCancel={() => actionMutation.mutate({ kind: "cancel", id: swap.id })}
          />
        ))}
      </section>
    </div>
  );
}

function SwapCard({
  swap,
  disabled,
  onAccept,
  onReject,
  onCancel,
}: {
  swap: SwapRequest;
  disabled: boolean;
  onAccept: () => void;
  onReject: () => void;
  onCancel: () => void;
}) {
  return (
    <article className="app-card">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className="font-bold text-slate-950">
            {swap.requester.songTitle} ↔ {swap.target.songTitle}
          </p>
          <p className="mt-2 text-sm text-slate-600">
            {formatRange(swap.requester.startAt, swap.requester.endAt)}
          </p>
          <p className="mt-1 text-sm text-slate-600">
            {formatRange(swap.target.startAt, swap.target.endAt)}
          </p>
        </div>
        <span className="count-badge">{statusLabel(swap.status)}</span>
      </div>

      {swap.status === "PENDING" && (
        <div className="mt-4 flex flex-wrap gap-2 border-t border-slate-100 pt-4">
          {swap.canAccept && (
            <button className="primary-button" type="button" disabled={disabled} onClick={onAccept}>
              수락
            </button>
          )}
          {swap.canReject && (
            <button className="secondary-button" type="button" disabled={disabled} onClick={onReject}>
              거절
            </button>
          )}
          {swap.canCancel && (
            <button className="danger-button" type="button" disabled={disabled} onClick={onCancel}>
              요청 취소
            </button>
          )}
        </div>
      )}
    </article>
  );
}

function statusLabel(status: SwapRequest["status"]) {
  if (status === "PENDING") return "대기 중";
  if (status === "ACCEPTED") return "교환 완료";
  if (status === "REJECTED") return "거절";
  if (status === "CANCELED") return "취소";
  return "만료";
}

function formatRange(startAt: string, endAt: string) {
  const date = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    month: "short",
    day: "numeric",
    weekday: "short",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(startAt));
  const end = new Intl.DateTimeFormat("ko-KR", {
    timeZone: "Asia/Seoul",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).format(new Date(endAt));
  return `${date} ~ ${end}`;
}
