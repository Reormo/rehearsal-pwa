import { describe, expect, it } from "vitest";
import manifest from "./manifest";

describe("PWA manifest", () => {
  it("keeps the installable standalone contract", () => {
    const value = manifest();

    expect(value.name).toBe("합주 예약");
    expect(value.short_name).toBe("합주");
    expect(value.start_url).toBe("/");
    expect(value.scope).toBe("/");
    expect(value.display).toBe("standalone");
    expect(value.lang).toBe("ko");

    expect(value.icons).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          src: "/icons/pwa-192x192.png",
          sizes: "192x192",
        }),
        expect.objectContaining({
          src: "/icons/pwa-512x512.png",
          sizes: "512x512",
        }),
        expect.objectContaining({
          src: "/icons/pwa-maskable-512x512.png",
          purpose: "maskable",
        }),
      ]),
    );
  });
});
