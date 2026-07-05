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

  cards: {
    headline: "There is an",
    headlineAccent: "elvo",
    headlineEnd: "for every need",
    description:
      "Every ELVO card is a controlled window into your wallet. Nothing is stored on the card itself. Everything is governed by the same rules that protect your balance. Join the waitlist for early access.",
    types: [
      {
        title: "ELVO Card",
        description: "Everyday spending, waitlist-first, with the same wallet rules and simpler checkout.",
        iconColor: "text-[#CC1E1E]",
      },
      {
        title: "ELVO Team Card",
        description: "Shared spending with permissions and approvals, built for teams on the way.",
        iconColor: "text-[#C29B42]",
      },
      {
        title: "ELVO Shield Card",
        description: "Fresh virtual numbers per purchase, freeze anytime, designed for safer online spend.",
        iconColor: "text-[#162A2C]",
      },
    ] satisfies CardType[],
    imageAlt: "ELVO card concepts: Standard, Team, and Shield",
  },

  transfer: {
    title: "Send money, easier than ever.",
    description:
      "Send to anyone on ELVO in seconds. Every transfer is checked against your limits and account rules first, so it either goes through cleanly or doesn't go at all.",
    features: [
      "Checked Before It Moves",
      "Limit Checks Included",
      "Full Transaction History",
    ],
    ctaLabel: "Send money",
  },

  bills: {
    title: "Check the bill before you pay it.",
    description:
      "Look up your LUKU, water, or DSTV account first, confirm the exact amount and details, then pay directly from your wallet.",
    features: [
      "8+ Bill Categories",
      "Lookup Before You Pay",
      "Status Tracked to Completion",
    ],
    ctaLabel: "Pay a bill",
  },

  everydayWallet: {
    sectionTitle: "Meet Your",
    sectionTitleAccent: "Everyday",
    sectionTitleEnd: "Wallet",
    sectionDescription:
      "From daily spending to what's coming next, your wallet is designed to grow with you, with sending, bills, and security tools built in.",
    sendReceive: {
      title: "Send and receive from contacts",
      description:
        "Send, receive, and track transfers with real-time status. Limits are checked before every move, and history is always within reach.",
    },
    bills: {
      title: "Pay bills instantly, all in one wallet",
      description:
        "Look up, confirm, and pay LUKU, water, TV, and more in one tap from lookup to receipt.",
    },
    multiUse: {
      chip: "Built for daylife",
      title: "One wallet built for every part of life",
      description:
        "Your money today, with family accounts and team tools on the way, all on the same trusted foundation.",
    },
    noSurprise: {
      chip: "No surprise charges",
      title: "Say goodbye to surprise charges",
      description:
        "You'll only pay what's shown upfront, with no hidden fees and a clear, reliable payment experience.",
    },
  },
