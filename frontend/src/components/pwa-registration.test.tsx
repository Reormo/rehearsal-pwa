import { cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { PwaRegistration } from "./pwa-registration";

describe("PwaRegistration", () => {
  const register = vi.fn();

  beforeEach(() => {
    register.mockReset();
    register.mockResolvedValue({});

    Object.defineProperty(navigator, "serviceWorker", {
      configurable: true,
      value: { register },
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
  });
});
