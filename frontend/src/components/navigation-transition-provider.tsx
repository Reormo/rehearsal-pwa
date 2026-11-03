"use client";

import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";

export type PageTransitionDirection = "left" | "right";

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
  const [direction, setDirection] =
    useState<PageTransitionDirection | null>(null);

  useEffect(() => {
    const handlePopState = () => {
      setDirection("left");
    };

    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  const value = useMemo(
    () => ({
      direction,
      startTransition: setDirection,
      clearTransition: () => setDirection(null),
    }),
    [direction],
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
