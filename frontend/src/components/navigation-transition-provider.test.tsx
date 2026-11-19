"use client";

import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  NavigationTransitionProvider,
  useNavigationTransition,
} from "./navigation-transition-provider";

const navigation = vi.hoisted(() => ({ pathname: "/" }));

vi.mock("next/navigation", () => ({
  usePathname: () => navigation.pathname,
}));

function TransitionProbe() {
  const { direction, footerMotion, startTransition, clearTransition } =
    useNavigationTransition();

  return (
    <>
      <output data-testid="direction">{direction ?? "none"}</output>
      <output data-testid="footer-motion">{footerMotion ?? "none"}</output>
      <button onClick={() => startTransition("right")}>Go right</button>
      <button onClick={() => startTransition("right", "/mt")}>Enter MT</button>
      <button onClick={() => startTransition("left", "/my")}>Leave MT</button>
      <button onClick={() => startTransition("left")}>Go left</button>
      <button onClick={clearTransition}>Finish animation</button>
    </>
  );
}

function tree() {
  return (
    <NavigationTransitionProvider>
      <TransitionProbe />
    </NavigationTransitionProvider>
  );
}

describe("NavigationTransitionProvider", () => {
  beforeEach(() => {
    navigation.pathname = "/";
  });

  afterEach(() => {
    cleanup();
  });

  it("waits until the destination route renders before animating", () => {
    const view = render(tree());

    fireEvent.click(screen.getByRole("button", { name: "Go right" }));
    expect(screen.getByTestId("direction")).toHaveTextContent("none");

    navigation.pathname = "/schedule";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("right");

    fireEvent.click(screen.getByRole("button", { name: "Finish animation" }));
    expect(screen.getByTestId("direction")).toHaveTextContent("none");
  });

  it("uses the new navigation direction without animating the source page", () => {
    const view = render(tree());

    fireEvent.click(screen.getByRole("button", { name: "Go right" }));
    fireEvent.click(screen.getByRole("button", { name: "Go left" }));
    expect(screen.getByTestId("direction")).toHaveTextContent("none");

    navigation.pathname = "/announcements";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("left");
  });

  it("does not animate navigation between two subpages", () => {
    navigation.pathname = "/my/teams";
    const view = render(tree());
    fireEvent.click(screen.getByRole("button", { name: "Go right" }));
    navigation.pathname = "/songs";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("none");
  });

  it("animates forward navigation between two MT subpages", () => {
    navigation.pathname="/mt/host";
    const view=render(tree());
    fireEvent.click(screen.getByRole("button",{name:"Go right"}));
    navigation.pathname="/mt/host/teams";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("right");
  });

  it("animates backward navigation between MT subpages", () => {
    navigation.pathname="/mt/typing";
    const view=render(tree());
    fireEvent.click(screen.getByRole("button",{name:"Go left"}));
    navigation.pathname="/mt";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("left");
  });

  it("slides the footer away only when entering MT from the regular app",()=>{
    navigation.pathname="/my";
    const view=render(tree());
    fireEvent.click(screen.getByRole("button",{name:"Enter MT"}));
    navigation.pathname="/mt";
    view.rerender(tree());
    expect(screen.getByTestId("footer-motion")).toHaveTextContent("exit");
    navigation.pathname="/mt/typing";
    view.rerender(tree());
    expect(screen.getByTestId("footer-motion")).toHaveTextContent("none");
  });

  it("slides the footer back when leaving MT for the full menu",()=>{
    navigation.pathname="/mt/typing";
    const view=render(tree());
    fireEvent.click(screen.getByRole("button",{name:"Leave MT"}));
    navigation.pathname="/my";
    view.rerender(tree());
    expect(screen.getByTestId("footer-motion")).toHaveTextContent("enter");
  });

  it("animates a subpage returning to its parent tab", () => {
    navigation.pathname = "/my/teams";
    const view = render(tree());
    fireEvent.click(screen.getByRole("button", { name: "Go right" }));
    navigation.pathname = "/my";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("right");
  });

  it("keeps browser back between subpages instantaneous", () => {
    navigation.pathname = "/my/reservations";
    const view = render(tree());
    window.dispatchEvent(new PopStateEvent("popstate"));
    navigation.pathname = "/my/teams";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("none");
  });

  it("preserves left-entry animation for browser back navigation", () => {
    navigation.pathname = "/schedule";
    const view = render(tree());

    window.dispatchEvent(new PopStateEvent("popstate"));
    expect(screen.getByTestId("direction")).toHaveTextContent("none");

    navigation.pathname = "/";
    view.rerender(tree());
    expect(screen.getByTestId("direction")).toHaveTextContent("left");
  });
});
