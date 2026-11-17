"use client";

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { RehearsalScheduleModal } from "./rehearsal-schedule-modal";

vi.mock("./rehearsal-calendar", () => ({
  RehearsalCalendar: () => <div>달력 내용</div>,
}));

afterEach(() => {
  cleanup();
  document.body.style.overflow = "";
});

describe("RehearsalScheduleModal", () => {
  it("shows the reusable calendar and closes with X", () => {
    const onClose = vi.fn();
    render(<RehearsalScheduleModal onClose={onClose} />);
    expect(screen.getByRole("dialog", { name: "전체 합주 일정" })).toBeInTheDocument();
    expect(screen.getByText("달력 내용")).toBeInTheDocument();
    expect(document.body.style.overflow).toBe("hidden");
    expect(screen.getByRole("dialog")).toHaveClass("h-[calc(100dvh-2rem)]", "[scrollbar-gutter:stable]");
    fireEvent.click(screen.getByRole("button", { name: "전체 합주 일정 닫기" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("closes with Escape and restores focus after unmount", () => {
    const trigger = document.createElement("button");
    document.body.appendChild(trigger);
    trigger.focus();
    const onClose = vi.fn();
    const view = render(<RehearsalScheduleModal onClose={onClose} />);
    expect(screen.getByRole("button", { name: "전체 합주 일정 닫기" })).toHaveFocus();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(onClose).toHaveBeenCalledOnce();
    view.unmount();
    expect(trigger).toHaveFocus();
    expect(document.body.style.overflow).toBe("");
    trigger.remove();
  });
});
