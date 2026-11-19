"use client";

import { usePathname } from "next/navigation";
import { isMtPath,normalizeAppPath } from "@/lib/mt-navigation";
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
  toPathname?: string;
};

type NavigationTransitionValue = {
  direction: PageTransitionDirection | null;
  footerMotion: "enter" | "exit" | null;
  startTransition: (direction: PageTransitionDirection, toPathname?: string) => void;
  clearTransition: () => void;
};

const NavigationTransitionContext =
  createContext<NavigationTransitionValue | null>(null);

export function NavigationTransitionProvider({
  children,
}: {
  children: React.ReactNode;
}) {
  const pathname = normalizeAppPath(usePathname());
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

  const value = useMemo<NavigationTransitionValue>(
    () => ({
      // Do not animate the page we are leaving while the destination loads.
      direction:
        pendingTransition &&
        pendingTransition.fromPathname !== pathname &&
        (!pendingTransition.toPathname || pendingTransition.toPathname === pathname) &&
        (!(isSubpage(pendingTransition.fromPathname) && isSubpage(pathname)) ||
          isMtRoute(pendingTransition.fromPathname) || isMtRoute(pathname))
          ? pendingTransition.direction
          : null,
      // The footer motion belongs to its destination route only. It must not
      // replay on a subsequent MT-to-MT navigation while a prior transition is pending.
      footerMotion: pendingTransition?.toPathname === pathname &&
        pendingTransition.fromPathname !== pathname
        ? isMtRoute(pendingTransition.fromPathname) && !isMtRoute(pathname)
          ? "enter"
          : !isMtRoute(pendingTransition.fromPathname) && isMtRoute(pathname)
            ? "exit"
            : null
        : null,
      startTransition: (direction: PageTransitionDirection, toPathname?: string) =>
        setPendingTransition({
          direction,
          fromPathname: lastRenderedPathname.current,
          toPathname:toPathname?normalizeAppPath(toPathname):undefined,
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

function isMtRoute(pathname:string) {
  return isMtPath(pathname);
}

function isSubpage(pathname:string) {
  return !["/","/schedule","/announcements","/admin","/my"].includes(normalizeAppPath(pathname));
}
