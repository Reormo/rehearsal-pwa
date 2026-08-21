"use client";

import { FormEvent, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import Link from "next/link";
import { AppShell } from "@/components/app-shell";
import { AuthGate } from "@/components/auth-gate";
import { errorMessage } from "@/lib/api";
import { scheduleAdminApi } from "@/lib/schedule-api";
import { adminSwapApi, SwapRequest } from "@/lib/swap-api";

export default function AdminSwapsPage() {
  return (
    <AuthGate adminOnly>
      {(user) => (
        <AppShell user={user}>
          <AdminSwapsContent />
        </AppShell>
      )}
    </AuthGate>
  );
}

function AdminSwapsContent() {
  const queryClient = useQueryClient();
  const swapsQuery = useQuery({
    queryKey: ["admin", "swaps"],
    queryFn: () => adminSwapApi.list(),
  });
  const reservationsQuery = useQuery({
    queryKey: ["admin", "reservations"],
    queryFn: scheduleAdminApi.adminReservations,
  });

  const refresh = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ["admin", "swaps"] }),
      queryClient.invalidateQueries({ queryKey: ["swaps"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "reservations"] }),
      queryClient.invalidateQueries({ queryKey: ["reservations", "mine"] }),
      queryClient.invalidateQueries({ queryKey: ["schedule"] }),
      queryClient.invalidateQueries({ queryKey: ["notifications"] }),
      queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
    ]);
  };

  const directMutation = useMutation({
    mutationFn: ({ firstId, secondId, reason }: { firstId: number; secondId: number; reason: string }) =>
      adminSwapApi.direct(firstId, secondId, reason),
    onSuccess: refresh,
  });
  const reviewMutation = useMutation({
    mutationFn: ({ kind, id, reason }: { kind: "accept" | "reject"; id: number; reason: string }) =>
      kind === "accept"
        ? adminSwapApi.accept(id, reason)
        : adminSwapApi.reject(id, reason),
    onSuccess: refresh,
  });

  const error = directMutation.error ?? reviewMutation.error ?? swapsQuery.error;

  function submitDirect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    directMutation.mutate({
      firstId: Number(form.get("firstReservationId")),
      secondId: Number(form.get("secondReservationId")),
      reason: String(form.get("reason")).trim(),
    });
  }

  return (
    <div className="space-y-7">
      <section className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="eyebrow">ADMIN · SWAPS</p>
          <h1 className="mt-2 text-3xl font-bold tracking-tight text-slate-950">
            일정 교환 관리
          </h1>
          <p className="mt-3 text-sm leading-6 text-slate-500">
            교환 요청을 허가·반려하거나, 관리자 권한으로 두 ACTIVE 예약을 직접 교환할 수 있습니다.
          </p>
        </div>
        <Link href="/admin/reservations" className="secondary-button">
          예약 강제 관리로
        </Link>
      </section>

      {error && <p className="error-box">{errorMessage(error)}</p>}

      <section className="app-card">
        <p className="card-label">관리자 직접 교환</p>
        <form className="mt-4 grid gap-3 sm:grid-cols-2" onSubmit={submitDirect}>
          <ReservationSelect name="firstReservationId" label="첫 번째 예약" reservations={reservationsQuery.data ?? []} />
          <ReservationSelect name="secondReservationId" label="두 번째 예약" reservations={reservationsQuery.data ?? []} />
          <label className="sm:col-span-2">
            <span className="card-label">사유</span>
            <input className="field-input mt-2" name="reason" maxLength={500} required />
          </label>
          <div className="sm:col-span-2">
            <button className="primary-button" type="submit" disabled={directMutation.isPending}>
              {directMutation.isPending ? "교환 중..." : "두 예약 직접 교환"}
            </button>
          </div>
        </form>
      </section>

      <section className="space-y-3">
        <div>
          <p className="card-label">전체 교환 요청</p>
          <h2 className="mt-2 text-lg font-bold text-slate-950">교환 신청 내역</h2>
        </div>
        {swapsQuery.isPending && <p className="app-card text-sm text-slate-400">불러오는 중...</p>}
        {swapsQuery.data?.length === 0 && (
          <p className="app-card text-sm text-slate-500">교환 요청이 없습니다.</p>
        )}
        {swapsQuery.data?.map((swap) => (
          <AdminSwapCard
            key={swap.id}
            swap={swap}
            disabled={reviewMutation.isPending}
            onReview={(kind, reason) => reviewMutation.mutate({ kind, id: swap.id, reason })}
          />
        ))}
      </section>
    </div>
  );
}

function ReservationSelect({
  name,
  label,
  reservations,
}: {
  name: string;
  label: string;
  reservations: Awaited<ReturnType<typeof scheduleAdminApi.adminReservations>>;
}) {
  return (
    <label>
      <span className="card-label">{label}</span>
      <select className="field-input mt-2" name={name} required>
        <option value="">선택</option>
        {reservations.map((reservation) => (
          <option key={reservation.id} value={reservation.id}>
            {reservation.songTitle} · {formatRange(reservation.startAt, reservation.endAt)}
          </option>
        ))}
      </select>
    </label>
  );
}

function AdminSwapCard({
  swap,
  disabled,
  onReview,
}: {
  swap: SwapRequest;
  disabled: boolean;
  onReview: (kind: "accept" | "reject", reason: string) => void;
}) {
  const [reason, setReason] = useState("");
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
        <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
          <input
            className="field-input"
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            maxLength={500}
            placeholder="관리자 허가/반려 사유"
          />
          <div className="flex flex-wrap gap-2">
            <button
              className="primary-button"
              type="button"
              disabled={disabled || !reason.trim()}
              onClick={() => onReview("accept", reason.trim())}
            >
              허가
            </button>
            <button
              className="danger-button"
              type="button"
              disabled={disabled || !reason.trim()}
              onClick={() => onReview("reject", reason.trim())}
            >
              반려
            </button>
          </div>
        </div>
      )}
    </article>
  );
}

function statusLabel(status: SwapRequest["status"]) {
  if (status === "PENDING") return "대기 중";
  if (status === "ACCEPTED") return "교환 완료";
  if (status === "REJECTED") return "반려/거절";
  if (status === "CANCELED") return "취소";
  return "만료";
}

function formatRange(startAt: string, endAt: string) {
  const start = new Intl.DateTimeFormat("ko-KR", {
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
  return `${start} ~ ${end}`;
}
