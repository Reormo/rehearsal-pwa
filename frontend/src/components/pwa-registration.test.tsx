import {
  act,
  cleanup,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PwaRegistration } from "./pwa-registration";

describe("PwaRegistration", () => {
  const register = vi.fn();
  const update = vi.fn();
  const addEventListener = vi.fn();
  const removeEventListener = vi.fn();
  let messageHandler: ((event: MessageEvent) => void) | null = null;

  beforeEach(() => {
    register.mockReset();
    update.mockReset();
    addEventListener.mockReset();
    removeEventListener.mockReset();
    update.mockResolvedValue(undefined);
    register.mockResolvedValue({ update });
    messageHandler = null;

    addEventListener.mockImplementation(
      (type: string, handler: (event: MessageEvent) => void) => {
        if (type === "message") {
          messageHandler = handler;
        }
      },
    );

    Object.defineProperty(navigator, "serviceWorker", {
      configurable: true,
      value: {
        controller: null,
        register,
        addEventListener,
        removeEventListener,
      },
    });
  });

  afterEach(() => {
    cleanup();
  });

  it("registers the single root-scope worker without HTTP cache reuse", async () => {
    render(<PwaRegistration />);

    await waitFor(() => {
      expect(register).toHaveBeenCalledWith("/sw.js", {
        scope: "/",
        updateViaCache: "none",
      });
    });

    expect(register).toHaveBeenCalledTimes(1);
    await waitFor(() => {
      expect(update).toHaveBeenCalledTimes(1);
    });
  });

  it("shows an in-app heads-up banner when a push message arrives", async () => {
    render(<PwaRegistration />);

    await waitFor(() => {
      expect(messageHandler).not.toBeNull();
    });

    act(() => {
      messageHandler?.({
        data: {
          type: "MUHON_PUSH_RECEIVED",
          payload: {
            title: "푸시 팝업 테스트",
            body: "화면 상단에 보이면 성공입니다.",
            linkPath: "/notifications",
          },
        },
      } as MessageEvent);
    });

    expect(screen.getByText("푸시 팝업 테스트")).toBeTruthy();
    expect(screen.getByText("화면 상단에 보이면 성공입니다.")).toBeTruthy();
  });
});
