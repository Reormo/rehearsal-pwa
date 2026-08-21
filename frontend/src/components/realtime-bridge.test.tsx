"use client";

import { act, cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { RealtimeBridge } from "./realtime-bridge";

const stompState = vi.hoisted(() => ({
  options: null as { onConnect?: () => void } | null,
  activate: vi.fn(),
  deactivate: vi.fn(),
  subscribe: vi.fn(),
}));

const queryState = vi.hoisted(() => ({
  invalidateQueries: vi.fn(),
}));

vi.mock("@stomp/stompjs", () => ({
  Client: class {
    constructor(options: { onConnect?: () => void }) {
      stompState.options = options;
    }

    activate() {
      stompState.activate();
    }

    deactivate() {
      stompState.deactivate();
      return Promise.resolve();
    }

    subscribe(destination: string, callback: () => void) {
      stompState.subscribe(destination, callback);
      return { unsubscribe: vi.fn() };
    }
  },
}));

vi.mock("@tanstack/react-query", () => ({
  useQueryClient: () => queryState,
}));

describe("RealtimeBridge", () => {
  beforeEach(() => {
    stompState.options = null;
    stompState.activate.mockReset();
    stompState.deactivate.mockReset();
    stompState.subscribe.mockReset();
    queryState.invalidateQueries.mockReset();
    queryState.invalidateQueries.mockResolvedValue(undefined);
  });

  afterEach(() => {
    cleanup();
  });

  it("does not open a websocket before the authenticated club is known", () => {
    render(<RealtimeBridge userId={null} clubId={null} />);

    expect(stompState.activate).not.toHaveBeenCalled();
  });

  it("subscribes to the club schedule and invalidates realtime query roots", async () => {
    const view = render(<RealtimeBridge userId={42} clubId={7} />);

    expect(stompState.activate).toHaveBeenCalledTimes(1);

    act(() => {
      stompState.options?.onConnect?.();
    });

    expect(stompState.subscribe).toHaveBeenCalledWith(
      "/topic/clubs/7/schedule",
      expect.any(Function),
    );

    const callback = stompState.subscribe.mock.calls[0]?.[1] as
      | (() => void)
      | undefined;
    expect(callback).toBeTypeOf("function");

    await act(async () => {
      callback?.();
      await Promise.resolve();
    });

    const expectedRoots = [
      ["schedule"],
      ["reservations"],
      ["swaps"],
      ["notifications"],
      ["admin", "reservations"],
      ["admin", "swaps"],
      ["admin", "schedule"],
      ["admin", "operating-hours"],
      ["admin", "action-logs"],
    ];

    await waitFor(() => {
      expect(queryState.invalidateQueries).toHaveBeenCalledTimes(
        expectedRoots.length,
      );
    });

    for (const queryKey of expectedRoots) {
      expect(queryState.invalidateQueries).toHaveBeenCalledWith({ queryKey });
    }

    view.unmount();
    expect(stompState.deactivate).toHaveBeenCalledTimes(1);
  });
});
