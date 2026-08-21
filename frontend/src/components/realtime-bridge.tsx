"use client";

import { Client } from "@stomp/stompjs";
import { useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";

const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL?.replace(/\/$/, "") ??
  "http://localhost:8080";

export function RealtimeBridge({
  userId,
  clubId,
}: {
  userId: number | null;
  clubId: number | null;
}) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (userId == null || clubId == null) {
      return;
    }

    const client = new Client({
      brokerURL: websocketUrl(),
      reconnectDelay: 3_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      connectionTimeout: 8_000,
      onConnect: () => {
        client.subscribe(`/topic/clubs/${clubId}/schedule`, () => {
          void refreshRealtimeQueries(queryClient);
        });
      },
    });

    client.activate();

    return () => {
      void client.deactivate();
    };
  }, [clubId, queryClient, userId]);

  return null;
}

function websocketUrl() {
  const url = new URL(API_BASE_URL);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = `${url.pathname.replace(/\/$/, "")}/ws`;
  url.search = "";
  url.hash = "";
  return url.toString();
}

async function refreshRealtimeQueries(
  queryClient: ReturnType<typeof useQueryClient>,
) {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ["schedule"] }),
    queryClient.invalidateQueries({ queryKey: ["reservations"] }),
    queryClient.invalidateQueries({ queryKey: ["swaps"] }),
    queryClient.invalidateQueries({ queryKey: ["notifications"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "reservations"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "swaps"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "schedule"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "operating-hours"] }),
    queryClient.invalidateQueries({ queryKey: ["admin", "action-logs"] }),
  ]);
}
