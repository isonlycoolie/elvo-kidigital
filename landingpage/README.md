# ELVO Digital Landing Page

Marketing site for **ELVO Digital**, a trusted digital wallet and payments platform built for everyday money in East Africa. Send, pay bills, and stay in control, with security and clarity at every step.

<p align="center">
  <img src="../docs/landing-hero.png" alt="ELVO Digital landing page hero: send, pay, and stay in control" width="100%" />
</p>

---

## Overview

This repository contains the public-facing landing page for ELVO. It presents the product vision, core capabilities, and roadmap in a modern, responsive layout, alongside the backend platform in the [main monorepo](https://github.com/isonlycoolie/elvo-kidigital).

The page is optimized for clarity and trust: wallet transfers, bill payments, security controls, and upcoming features (agents, family accounts, delegated access) are explained in plain language without over-promising capabilities still in beta.

---

## Documentation

Platform docs live in the monorepo [`docs/`](../docs/) folder:

| Resource | Description |
|----------|-------------|
| [docs/README.md](../docs/README.md) | Documentation index with landing preview |
| [docs/INTEGRATION-CONTRACTS.md](../docs/INTEGRATION-CONTRACTS.md) | Cross-service API integration contract |
| [docs/landing-hero.png](../docs/landing-hero.png) | Hero section screenshot |

---

## Highlights

| Area | What visitors see |
|------|-------------------|
| **Hero** | Product positioning, trust badge, and app preview |
| **Features** | Unified bill pay, lookup-before-pay, built-in security |
| **Wallet flows** | Transfer and bills payment showcases |
| **Trust** | Security layer, cards (waitlist), and everyday wallet story |
| **FAQ** | Honest answers on wallet, bills, security, and roadmap |
| **Open source** | Link to the platform repository for contributors |

---

## Tech stack

- **[Next.js 16](https://nextjs.org/)** (App Router)
- **[React 19](https://react.dev/)** + **TypeScript**
- **[Tailwind CSS 4](https://tailwindcss.com/)**
- **[Framer Motion](https://www.framer.com/motion/)** for scroll and entrance animations
- **[Lucide React](https://lucide.dev/)** icons
- Centralized copy in [`src/content/site-copy.ts`](./src/content/site-copy.ts)

---

## Getting started

### Prerequisites

- Node.js 20+
- npm, yarn, pnpm, or bun

### Install and run

```bash
cd landingpage
npm install
npm run dev
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

### Production build

```bash
npm run build
npm start
```

### Lint

```bash
npm run lint
```

