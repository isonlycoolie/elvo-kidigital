# ELVO Digital

ELVO Digital is the **backend monorepo** for the ELVO financial platform—a set of Spring Boot microservices that handle who users are, how their accounts are governed, how money moves in wallets, and how bill payments are collected and settled.

The codebase is organized for **production-grade money flows**: strong authentication, explicit account rules before transfers, state-machine-driven payments with compensations, and full-stack observability (metrics, dashboards, error tracking) from day one of local development.

---

## Services

The sections below describe each bounded context in the platform. Four services run in the **default Docker Compose stack**; four others are **scaffolded** (project structure and health endpoints exist, business logic is not wired into Compose yet).

---

### elvo-identity-service

The identity service is the **authentication and trust layer** for the whole platform. Every other service that needs to know “who is this user?” and “is this session still valid?” depends on it.

**Local runtime:** port `8081` · database `identity_db` (PostgreSQL) · Redis · RabbitMQ

**What it owns**

- User registration (email and mobile paths) with a **pending-verification** lifecycle before full access
- Login, logout, refresh tokens, and password reset/update
- OTP verification and resend for email and mobile channels
- JWT access/refresh issuance; JWKS publication at `/.well-known`
- **ESP / EAC** step-up authentication for sensitive actions
- **Fast-login** challenge flows for trusted devices
- Session and device lifecycle (including logout-all)
- TOTP enrollment, backup recovery codes, and weighted risk scoring hooks
- Internal APIs for session verification and user status consumed by wallet and peers
- Audit events published to RabbitMQ after security-relevant actions

**Public API areas**

- `/auth/**` — register, login, OTP, tokens, password flows
- `/esp/**`, `/eac/**`, `/fast-login/**` — step-up and device trust
- `/users/me/**` — authenticated profile and security settings
- `/internal/**` — service-to-service identity checks

**Integrates with:** account-management (account reads during provisioning), wallet (post-verification provisioning), Redis (rate limits/cache), RabbitMQ (audit and async intents).

**Deeper docs:** [services/elvo-identity-service/README.md](services/elvo-identity-service/README.md)

---

### elvo-wallet-service

The wallet service is the **ledger and money-movement engine**. It holds balances, executes deposits and withdrawals, runs peer transfers, and coordinates with billing and account rules before funds move.

**Local runtime:** port `8082` · database `wallet_db` (PostgreSQL) · Redis · RabbitMQ

**What it owns**

- Wallet profile, balance, and transaction history for the authenticated user
- **Deposits**, **withdrawals**, and **P2P transfers** with validation against account-management rules
- **Device-free withdrawals** and delegated withdrawal token lifecycle
- Balance **reservations** (hold → release or confirm) for multi-step flows
- Self-service **freeze / unfreeze** on the user wallet
- Withdrawal codes (generate, list, redeem)
- Per-user limits surfaced at `/wallets/me/limits`
- **Transaction state machine** with retries, saga-style compensations, and event publishing
- **Fraud and risk** decision envelopes on privileged flows
- **Admin** surfaces: audit queries, fraud review, emergency controls
- **Internal** APIs for billing settlement, outbox replay, and resilience diagnostics
- EAC replay protection and privileged-access control for internal callers

**Public API areas**

- `/wallets/**` — customer wallet operations
- `/api/v1/internal/wallets/**` — internal settlement and orchestration
- `/api/v1/admin/**` — operations and fraud (protected)

**Integrates with:** identity (auth), account-management (transfer/withdrawal/receive validation, limits, permissions), billing (wallet debit/credit for bill pay), RabbitMQ (domain events), Prometheus/Sentry.

**Deeper docs:** [services/elvo-wallet-service/README.md](services/elvo-wallet-service/README.md)

---

### elvo-billing-service

The billing service handles **bill and utility payments**—lookup, initiation, status tracking, provider callbacks, and settlement against the user’s wallet through a formal payment state machine.

**Local runtime:** port `8083` · database `billing_db` (PostgreSQL) · Redis · RabbitMQ

**What it owns**

- Bill payment **lookup** and **initiation** (`POST /api/v1/bill-payments`, `/lookup`)
- Payment status by ID or external reference
- **Provider integration** (e.g. Selcom adapter) with callback handling at `/api/v1/internal/bill-payments/provider-callback`
- **Billing transaction state machine**—retries, compensations, and wallet settlement steps
- Consumption of **wallet completion/failure events** from dedicated queues
- Rate limiting on billing operations and internal event ingestion
- Immutable audit storage for payment and lookup activity
- MFA hooks on sensitive billing operations where configured

**Public API areas**

- `/api/v1/bill-payments/**` — customer-facing payment API
- `/api/v1/internal/bill-payments/**` — provider callbacks and internal hooks
- `/internal/**` — health and diagnostics

**Integrates with:** wallet (fund movement), RabbitMQ (wallet event consumers), Redis, Sentry (optional per-environment).

**Note:** No dedicated service README yet; see `services/elvo-billing-service/src/main/java` and `application.yml` for contracts.

---

### elvo-account-management-service

Account management is the **system of record for account structure and policy**—not for moving money, but for deciding whether an account may receive, send, or withdraw under current limits, permissions, and restrictions.

**Local runtime:** port `8084` · database `account_db` (PostgreSQL) · Redis · RabbitMQ

**What it owns**

- Account creation, lookup by ID, user, or **EAN** (Elvo Account Number)
- Pre-flight validation for **transfer**, **withdrawal**, and **receive**
- **Limit** and **permission** checks; maker-checker style **limit-change** and **permission-change** workflows
- Account lifecycle: activate, freeze, unfreeze, suspend, close, reopen, archive
- **Restrictions** (apply and remove) and **relationship** unlinking
- **Admin actions** with request/approve workflow
- Audit log persistence for governance changes
- Listener for identity registration intents (account provisioning path)

**API surface (internal only)**

- Base path: `/api/v1/internal/accounts`
- All endpoints are intended for **service-to-service** calls from identity and wallet, not direct mobile clients

**Integrates with:** identity (registration → account), wallet (validation on every money movement), RabbitMQ, Sentry.

**Deeper docs:** [services/elvo-account-management-service/README.md](services/elvo-account-management-service/README.md)

---

### Services in development

These modules share the same **Spring Boot + layered package layout** as production services (controller, service, messaging, security, audit) but are **not started** by `start-local.ps1` today. They expose health placeholders and are ready for feature work.

**elvo-notification-service** — Planned channel for SMS, email, and push delivery driven by domain events from identity, wallet, and billing. Will decouple “something happened” from “tell the user.”

**elvo-agent-service** — Planned surface for agent and branch-channel operations (cash-in/cash-out style flows, agent balances, and channel-specific limits).

**elvo-delegated-access-service** — Planned service for delegated access grants—who may act on whose wallet or account, with time bounds and approval rules.

**elvo-web-dashboard-service** — Planned BFF/API layer for an internal admin or operations dashboard (aggregated views, maker-checker UI backing APIs).

---

### How services connect

```text
                    ┌─────────────────────┐
                    │  identity (:8081) │
                    └──────────┬──────────┘
                               │ auth / provisioning events
           ┌───────────────────┼───────────────────┐
           ▼                   ▼                   ▼
┌──────────────────┐  ┌──────────────┐  ┌──────────────────┐
│ account-mgmt     │  │ wallet       │  │ billing          │
│ (:8084)          │◄─┤ (:8082)      │◄─┤ (:8083)          │
│ rules & lifecycle│  │ money ledger │  │ bill payments    │
└──────────────────┘  └──────┬───────┘  └──────────────────┘
                             │
                    RabbitMQ + Redis (shared)
                    PostgreSQL (one DB per service)
```

---

## Technology stack

| Layer | Choices |
|-------|---------|
| Language & runtime | Java 17 |
| Framework | Spring Boot 3.3.x (Web, Security, Data JPA, AMQP, Redis, Actuator) |
| Data | PostgreSQL 16 (per-service database), Flyway migrations |
| Messaging & cache | RabbitMQ 3, Redis 7 |
| Security | Spring Security, JWT (identity), internal service auth matrices |
| Payments / wallet orchestration | Custom state machines with retry and compensation handlers |
| Observability | Prometheus scrape, Grafana dashboards, Sentry (per-service DSN/release) |
| Local orchestration | Docker Compose (`infrastructure/docker/`) |
| Build | Maven 3.9+ per service module |

Shared configuration templates live under `shared/` (for example Sentry config patterns). Optional shared Java libraries can be enabled per service via the `with-shared-libs` Maven profile.

---

## Repository layout

```text
elvo-digital/
├── services/
│   ├── elvo-identity-service/           # Auth, sessions, MFA, OTP
│   ├── elvo-wallet-service/             # Balances, transfers, reservations
│   ├── elvo-billing-service/            # Bill pay and provider settlement
│   ├── elvo-account-management-service/ # Accounts, limits, permissions
│   ├── elvo-notification-service/     # Scaffold
│   ├── elvo-agent-service/            # Scaffold
│   ├── elvo-delegated-access-service/   # Scaffold
│   └── elvo-web-dashboard-service/    # Scaffold
├── infrastructure/
│   ├── docker/          # compose.base.yml, compose.infrastructure.yml, compose.services.yml
│   ├── postgres/        # SQL scripts and migration helpers
│   ├── rabbitmq/        # Broker user/setup scripts
│   ├── scripts/         # start-local, stop-local, reset-local, smoke, build-shared-first
│   └── k8s/             # Environment layering examples
├── monitoring/
│   ├── prometheus/      # Scrape configs and alert rules
│   ├── grafana/         # Service dashboards (identity, wallet, billing, account)
│   ├── alertmanager/
│   └── sentry/          # Integration guide
├── shared/              # Cross-cutting config templates
├── .env.shared.example  # Shared infra variables for Docker
└── README.md            # This file
```

Each service follows a consistent package shape: `controller` → `service` → `repository` → `entity`, plus `messaging`, `security`, `audit`, and `config` where needed.

---

## Local development

### Prerequisites

- **JDK 17+** and **Maven 3.9+**
- **Docker Desktop** for the integrated stack

### Environment and secrets

Runtime secrets are **never** committed. On first setup:

1. Copy [`.env.shared.example`](.env.shared.example) → `.env.shared` at the repo root.
2. For each running service, copy `services/<name>/.env.example` → `services/<name>/.env`.
3. Fill in passwords, JWT keys, and provider credentials locally only.

Git ignores `.env`, `.env.shared`, `services/*/.env`, `docs/`, and `.github/`.

### Start, stop, and reset

From the repository root:

```powershell
# Build modules and start identity, wallet, billing, account-mgmt + infra
.\infrastructure\scripts\start-local.ps1

# Optional: Prometheus, Grafana, Alertmanager
.\infrastructure\scripts\start-local.ps1 -IncludeMonitoring

.\infrastructure\scripts\stop-local.ps1
.\infrastructure\scripts\reset-local.ps1
```

After startup, typical endpoints:

| Endpoint | URL |
|----------|-----|
| Identity health | http://localhost:8081/actuator/health |
| Wallet health | http://localhost:8082/actuator/health |
| Billing health | http://localhost:8083/actuator/health |
| Account health | http://localhost:8084/actuator/health |
| RabbitMQ management | http://localhost:15672 (default guest/guest) |

### Run tests for one service

```powershell
cd services/elvo-wallet-service
mvn test
```

---

## Observability

All primary services expose Spring Actuator **`/actuator/health`** and **`/actuator/prometheus`** when running.

- **Prometheus** configs: [`monitoring/prometheus/`](monitoring/prometheus/)
- **Grafana** dashboards: [`monitoring/grafana/`](monitoring/grafana/) (wallet, identity, billing, account-management)
- **Sentry** setup: [`monitoring/sentry/README.md`](monitoring/sentry/README.md)

Wallet publishes rich custom metrics (`wallet_transactions_total`, reservation and freeze counters, saga compensations, event publish outcomes). Panel definitions and scrape fragments are documented in the wallet service README.

---

## Design principles

- **Bounded contexts** — Each service owns its PostgreSQL schema; no shared tables across domains.
- **Internal vs public APIs** — Customer flows use authenticated public routes; policy and settlement use explicit internal paths.
- **Fail closed** — Unknown auth state, failed limit checks, or missing permissions block the operation.
- **Auditability** — Security and money events produce structured audit records and often RabbitMQ events.
- **Correlation** — Request and correlation IDs propagate across HTTP and messaging for traceable incidents.
- **Resilience** — Wallet and billing flows use state machines, idempotency keys, retries, and compensations instead of fire-and-forget updates.

---

## Further reading

- Identity: [services/elvo-identity-service/README.md](services/elvo-identity-service/README.md)
- Wallet: [services/elvo-wallet-service/README.md](services/elvo-wallet-service/README.md)
- Account management: [services/elvo-account-management-service/README.md](services/elvo-account-management-service/README.md)
- Sentry: [monitoring/sentry/README.md](monitoring/sentry/README.md)

---

## License

Proprietary — ELVO platform. All rights reserved unless otherwise stated by the repository owner.
