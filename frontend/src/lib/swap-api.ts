import { request } from "@/lib/api";

export type SwapStatus =
  | "PENDING"
  | "ACCEPTED"
  | "REJECTED"
  | "CANCELED"
  | "EXPIRED";

export type SwapReservationSummary = {
  reservationId: number;
  songId: number;
  songTitle: string;
  startAt: string;
  endAt: string;
};

export type SwapRequest = {
  id: number;
  status: SwapStatus;
  requester: SwapReservationSummary;
  target: SwapReservationSummary;
  requestedBy: number;
  respondedBy: number | null;
  requestedAt: string;
  respondedAt: string | null;
  expiredAt: string | null;
  canAccept: boolean;
  canReject: boolean;
  canCancel: boolean;
};

export type SwapCandidate = {
  reservationId: number;
  songId: number;
  songTitle: string;
  startAt: string;
  endAt: string;
};

export const swapApi = {
  mine() {
    return request<SwapRequest[]>("/api/swaps");
  },

  candidates(requesterReservationId: number) {
    return request<SwapCandidate[]>(
      `/api/swaps/candidates?requesterReservationId=${encodeURIComponent(requesterReservationId)}`,
    );
  },

  create(requesterReservationId: number, targetReservationId: number) {
    return request<SwapRequest>("/api/swaps", {
      method: "POST",
      body: JSON.stringify({ requesterReservationId, targetReservationId }),
    });
  },

  accept(swapRequestId: number) {
    return request<SwapRequest>(`/api/swaps/${swapRequestId}/accept`, {
      method: "POST",
    });
  },

  reject(swapRequestId: number) {
    return request<SwapRequest>(`/api/swaps/${swapRequestId}/reject`, {
      method: "POST",
    });
  },

  cancel(swapRequestId: number) {
    return request<SwapRequest>(`/api/swaps/${swapRequestId}/cancel`, {
      method: "POST",
    });
  },
};

export const adminSwapApi = {
  list(status?: SwapStatus) {
    const suffix = status ? `?status=${encodeURIComponent(status)}` : "";
    return request<SwapRequest[]>(`/api/admin/swaps${suffix}`);
  },

  accept(swapRequestId: number, reason: string) {
    return request<SwapRequest>(`/api/admin/swaps/${swapRequestId}/accept`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
  },

  reject(swapRequestId: number, reason: string) {
    return request<SwapRequest>(`/api/admin/swaps/${swapRequestId}/reject`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
  },

  direct(firstReservationId: number, secondReservationId: number, reason: string) {
    return request<SwapRequest>("/api/admin/swaps/direct", {
      method: "POST",
      body: JSON.stringify({ firstReservationId, secondReservationId, reason }),
    });
  },
};
