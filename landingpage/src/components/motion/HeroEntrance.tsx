"use client";

import { motion, useReducedMotion } from "framer-motion";
import type { ReactNode } from "react";
import { duration, easeOut, revealDistance } from "@/lib/motion";
import { cn } from "@/lib/utils";

type HeroEntranceProps = {
  children: ReactNode;
  className?: string;
  delay?: number;
};

export function HeroEntrance({
  children,
  className,
  delay = 0,
}: HeroEntranceProps) {
  const shouldReduceMotion = useReducedMotion();

  if (shouldReduceMotion) {
    return <div className={className}>{children}</div>;
  }

  return (
    <motion.div
      className={cn(className)}
      initial={{ opacity: 0, y: revealDistance }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: duration.reveal, ease: easeOut, delay }}
    >
      {children}
    </motion.div>
  );
}
