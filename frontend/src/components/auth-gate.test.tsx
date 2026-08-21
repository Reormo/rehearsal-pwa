"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, authApi, type AuthUser } from "@/lib/api";
import { AuthGate } from "./auth-gate";

const routerState = vi.hoisted(() => ({
  replace: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({
    replace: routerState.replace,
  }),
}));

const member: AuthUser = {
  id: 7,
  loginId: "member07",
  name: "테스트 회원",
  clubId: 1,
  role: "MEMBER",
};

describe("AuthGate", () => {
  let queryClient: QueryClient;

  beforeEach(() => {
    routerState.replace.mockReset();
    queryClient = new QueryClient({
      defaultOptions: {
        queries: {
          retry: false,
        },
      },
    });
  });

  afterEach(() => {
    cleanup();
    queryClient.clear();
    vi.restoreAllMocks();
  });

  it("renders authenticated content", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(member);

    renderGate(false);

    expect(await screen.findByText("테스트 회원")).toBeInTheDocument();
  });

  it("blocks a member from an admin-only screen", async () => {
    vi.spyOn(authApi, "me").mockResolvedValue(member);

    renderGate(true);

    expect(
      await screen.findByText("관리자만 접근할 수 있는 화면입니다."),
    ).toBeInTheDocument();
  });

  it("redirects an unauthorized session to login", async () => {
    vi.spyOn(authApi, "me").mockRejectedValue(
      new ApiError(401, "UNAUTHORIZED", "로그인이 필요합니다."),
    );

    renderGate(false);

    await waitFor(() => {
      expect(routerState.replace).toHaveBeenCalledWith("/login");
    });
  });

  function renderGate(adminOnly: boolean) {
    return render(
      <QueryClientProvider client={queryClient}>
        <AuthGate adminOnly={adminOnly}>
          {(user) => <div>{user.name}</div>}
        </AuthGate>
      </QueryClientProvider>,
    );
  }
});
