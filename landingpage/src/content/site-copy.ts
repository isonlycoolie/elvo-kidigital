/**
 * Centralized landing page copy aligned with ELVO-PRODUCT-DESIGN-BRIEF v2.0.
 * Edit strings here. Components import from this module only.
 * Do not use em dashes (—) or en dashes (–); use commas, periods, colons, or semicolons.
 */

import { normalizeCopyTree } from "./normalize-copy";

export type FaqItem = {
  question: string;
  answer: string;
};

export type FeatureCard = {
  title: string;
  description: string;
  image: string;
  alt: string;
};

export type CardType = {
  title: string;
  description: string;
  iconColor: string;
};

export const siteCopy = normalizeCopyTree({
  hero: {
    badgeLabel: "Trusted everyday wallet",
    badgeStarCount: 5,
    headlineLines: ["Send, pay, and", "stay in control"],
    subheadlineLines: [
      "One wallet for everyday money. Send to friends,",
      "pay your bills, and manage it all with security built",
      "in from day one.",
    ],
    cta: "Github Repository",
    socialProof: "Built for East Africa",
  },
