"use client";

import { usePathname } from "next/navigation";
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from "react";

export type PageTransitionDirection = "left" | "right";

type PendingTransition = {
  direction: PageTransitionDirection;
  fromPathname: string;
};

type NavigationTransitionValue = {
  direction: PageTransitionDirection | null;
  startTransition: (direction: PageTransitionDirection) => void;
  clearTransition: () => void;
};

const NavigationTransitionContext =
  createContext<NavigationTransitionValue | null>(null);

export function NavigationTransitionProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = usePathname();
  const lastRenderedPathname = useRef(pathname);
  const [pendingTransition, setPendingTransition] =
    useState<PendingTransition | null>(null);

  useEffect(() => {
    lastRenderedPathname.current = pathname;
  }, [pathname]);

  useEffect(() => {
    const handlePopState = () => {
      setPendingTransition({
        direction: "left",
        fromPathname: lastRenderedPathname.current,
      });
    };

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  const value = useMemo(
    () => ({
      // Do not animate the page we are leaving while the destination loads.
      direction:
        pendingTransition &&
        pendingTransition.fromPathname !== pathname &&
        !(isSubpage(pendingTransition.fromPathname) && isSubpage(pathname))
          ? pendingTransition.direction
          : null,
      startTransition: (direction: PageTransitionDirection) =>
        setPendingTransition({
          direction,
          fromPathname: lastRenderedPathname.current,
        }),
      clearTransition: () => setPendingTransition(null),
    }),
    [pathname, pendingTransition],
  );

  return (
    <NavigationTransitionContext.Provider value={value}>
      {children}
    </NavigationTransitionContext.Provider>
  );
}

export function useNavigationTransition() {
  const value = useContext(NavigationTransitionContext);
  if (!value) {
    throw new Error(
      "useNavigationTransition must be used within NavigationTransitionProvider",
    );
  }
  return value;
}

function isSubpage(pathname: string) {
  const normalized = pathname.replace(/\/index\.html$/, "").replace(/\.html$/, "").replace(/\/+$/, "") || "/";
  return !["/", "/schedule", "/announcements", "/admin", "/my"].includes(normalized);
}
