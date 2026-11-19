import { describe, expect, it } from "vitest";
import { formatMtTime } from "./mt-time";

describe("formatMtTime",()=>{
  it("uses 24-hour Seoul time for UTC timestamps",()=>{
    expect(formatMtTime("2026-11-18T07:38:00Z")).toBe("16:38");
    expect(formatMtTime("2026-11-18T00:00:00Z")).toBe("09:00");
  });
  it("handles offset timestamps and invalid values",()=>{
    expect(formatMtTime("2026-11-18T16:38:00+09:00")).toBe("16:38");
    expect(formatMtTime("invalid")).toBe("--:--");
  });
});
