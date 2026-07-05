"use client";

import { motion, useReducedMotion } from "framer-motion";
import type { ReactNode } from "react";
import { duration, easeOut, revealDistance } from "@/lib/motion";
import { cn } from "@/lib/utils";

type RevealProps = {
  children: ReactNode;
  className?: string;
  delay?: number;
  as?: "div" | "section" | "article";
};

const motionComponents = {
  div: motion.div,
  section: motion.section,
  article: motion.article,
} as const;

export function Reveal({
  children,
  className,
  delay = 0,
  as = "div",
}: RevealProps) {
  const shouldReduceMotion = useReducedMotion();
  const Component = motionComponents[as];

  if (shouldReduceMotion) {
    const Static = as;
    return <Static className={className}>{children}</Static>;
  }

  return (
    <Component
      className={cn(className)}
      initial={{ opacity: 0, y: revealDistance }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, amount: 0.2, margin: "-40px" }}
      transition={{ duration: duration.reveal, ease: easeOut, delay }}
    >
      {children}
    </Component>
  );
}
