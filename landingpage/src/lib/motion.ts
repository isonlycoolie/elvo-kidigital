import type { Variants } from "framer-motion";

export const easeOut = [0.22, 1, 0.36, 1] as const;

export const duration = {
  fast: 0.2,
  reveal: 0.55,
} as const;

export const revealDistance = 16;

export const revealViewport = {
  once: true,
  amount: 0.2,
  margin: "-40px",
} as const;

export const fadeUp: Variants = {
  hidden: {
    opacity: 0,
    y: revealDistance,
  },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: duration.reveal,
      ease: easeOut,
    },
  },
};

export const fadeUpStagger: Variants = {
  hidden: { opacity: 0 },
  visible: {
    opacity: 1,
    transition: {
      staggerChildren: 0.08,
      delayChildren: 0.04,
    },
  },
};

export const fadeUpChild: Variants = {
  hidden: {
    opacity: 0,
    y: revealDistance,
  },
  visible: {
    opacity: 1,
    y: 0,
    transition: {
      duration: duration.reveal,
      ease: easeOut,
    },
  },
};
