"use client";

import { motion, useReducedMotion } from "framer-motion";
import type { ReactNode } from "react";
import { fadeUpChild, fadeUpStagger } from "@/lib/motion";
import { cn } from "@/lib/utils";

type RevealGroupProps = {
  children: ReactNode;
  className?: string;
  as?: "div" | "section" | "ul";
};

const motionComponents = {
  div: motion.div,
  section: motion.section,
  ul: motion.ul,
} as const;

export function RevealGroup({
  children,
  className,
  as = "div",
}: RevealGroupProps) {
  const shouldReduceMotion = useReducedMotion();
  const Component = motionComponents[as];

  if (shouldReduceMotion) {
    const Static = as;
    return <Static className={className}>{children}</Static>;
  }

  return (
    <Component
      className={cn(className)}
      initial="hidden"
      whileInView="visible"
      viewport={{ once: true, amount: 0.2, margin: "-40px" }}
      variants={fadeUpStagger}
    >
      {children}
    </Component>
  );
}

export function RevealItem({
  children,
  className,
  as = "div",
}: {
  children: ReactNode;
  className?: string;
  as?: "div" | "li" | "article";
}) {
  const shouldReduceMotion = useReducedMotion();
  const motionMap = {
    div: motion.div,
    li: motion.li,
    article: motion.article,
  } as const;
  const Component = motionMap[as];

  if (shouldReduceMotion) {
    const Static = as;
    return <Static className={className}>{children}</Static>;
  }

  return (
    <Component className={cn(className)} variants={fadeUpChild}>
      {children}
    </Component>
  );
}
