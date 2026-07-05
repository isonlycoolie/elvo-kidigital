"use client";

import { createContext, useContext, useState, ReactNode } from "react";

// This is a lightweight context setup.
// If application complexity increases, consider replacing this with Zustand.

interface GlobalState {
  isMenuOpen: boolean;
  setMenuOpen: (isOpen: boolean) => void;
}

const GlobalContext = createContext<GlobalState | undefined>(undefined);

export function GlobalProvider({ children }: { children: ReactNode }) {
  const [isMenuOpen, setMenuOpen] = useState(false);

  return (
    <GlobalContext.Provider value={{ isMenuOpen, setMenuOpen }}>
      {children}
    </GlobalContext.Provider>
  );
}

export function useGlobal() {
  const context = useContext(GlobalContext);
  if (context === undefined) {
    throw new Error("useGlobal must be used within a GlobalProvider");
  }
  return context;
}
