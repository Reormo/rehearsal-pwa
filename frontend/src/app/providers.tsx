"use client";

import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { useState } from "react";
import { RealtimeBridge } from "@/components/realtime-bridge";
import { AuthUser, authApi } from "@/lib/api";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 30_000,
            retry: false,
            refetchOnWindowFocus: false,
          },
          mutations: {
            retry: false,
          },
        },
      }),
  );

  return (
    <QueryClientProvider client={queryClient}>
      <RealtimeAuthObserver />
      {children}
    </QueryClientProvider>
  );
}

function RealtimeAuthObserver() {
  const authQuery = useQuery<AuthUser>({
    queryKey: ["auth", "me"],
    queryFn: authApi.me,
    enabled: false,
  });

  return (
    <RealtimeBridge
      userId={authQuery.data?.id ?? null}
      clubId={authQuery.data?.clubId ?? null}
    />
  );
}
