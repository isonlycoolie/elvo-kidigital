"use client";

import { ThemeProvider } from "@/providers/theme-provider";
import { Toaster } from "sonner";

import { GlobalProvider } from "@/contexts";

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      disableTransitionOnChange
    >
      <GlobalProvider>
        {children}
      </GlobalProvider>
      <Toaster position="bottom-right" richColors />
    </ThemeProvider>
  );
}
