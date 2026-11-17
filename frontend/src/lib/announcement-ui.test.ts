import { describe, expect, it } from "vitest";
import { announcementPreview } from "./announcement-ui";

describe("announcementPreview", () => {
  it("normalizes line breaks and truncates long unbroken contents", () => {
    expect(announcementPreview("   1234567890123\n4567   ", 10)).toBe("1234567890…");
  });

  it("keeps a short preview without adding ellipsis", () => {
    expect(announcementPreview("  합주 일정\n  확인 바랍니다.  ")).toBe("합주 일정 확인 바랍니다.");
  });
});
