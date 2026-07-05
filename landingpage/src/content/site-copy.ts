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

  features: {
    headline: "Tired of juggling five apps to",
    headlineAccent: "pay your bills?",
    description:
      "ELVO brings everyday money into one place. Check your balance, send to family, and pay LUKU, water, TV, or airtime without switching apps or memorizing USSD codes.",
    cards: [
      {
        title: "Pay bills in one place",
        description:
          "Electricity, water, TV, airtime, internet, government, hospital, and airline. Eight categories, one wallet.",
        image: "/images/features/payb-bills.svg",
        alt: "Pay bills interface",
      },
      {
        title: "Look up before you pay",
        description:
          "Check the bill first, confirm the amount and details, then pay, so every payment starts clear and ends tracked.",
        image: "/images/features/track-bills.svg",
        alt: "Bill lookup and confirmation flow",
      },
      {
        title: "Security built in",
        description:
          "Verification-first signup, step-up protection on sensitive moves, and limits you can see before money moves.",
        image: "/images/features/secure-payment.svg",
        alt: "Security and verification icons",
      },
    ] satisfies FeatureCard[],
  },
