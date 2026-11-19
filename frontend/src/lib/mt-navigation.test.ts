import { describe,expect,it } from "vitest";
import { isMtPath,mtBackTarget } from "./mt-navigation";

describe("MT navigation routes",()=>{
  it("uses a stable parent rather than arbitrary browser history",()=>{
    expect(mtBackTarget("/mt")).toBe("/my");
    expect(mtBackTarget("/mt/host")).toBe("/my");
    expect(mtBackTarget("/mt/buzzer")).toBe("/mt");
    expect(mtBackTarget("/mt/typing")).toBe("/mt");
    expect(mtBackTarget("/mt/host/teams")).toBe("/mt/host");
    expect(mtBackTarget("/mt/host/history")).toBe("/mt/host");
    expect(mtBackTarget("/schedule")).toBe(null);
  });
  it("recognizes only MT route prefixes",()=>{
    expect(isMtPath("/mt/host")).toBe(true);
    expect(isMtPath("/mt-other")).toBe(false);
  });
});
