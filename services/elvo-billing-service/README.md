# ELVO Billing Service

Bill and utility payment orchestration for the ELVO platform.

## Purpose

- Bill payment **lookup** and **initiation**
- Payment status tracking by ID or external reference
- Provider adapter integration (Selcom, mock)
- Wallet settlement via sync HTTP (reserve → confirm/release)
- Billing transaction state machine with retries and compensations

## Local runtime

- Port: `8083`
- Database: `billing_db` (PostgreSQL)
- Depends on: wallet service for settlement, Redis, RabbitMQ

## API areas

| Path | Description |
|------|-------------|
| `POST /api/v1/bill-payments/lookup` | Validate reference and retrieve bill details |
| `POST /api/v1/bill-payments` | Initiate payment |
| `GET /api/v1/bill-payments/{paymentId}` | Payment status |
| `GET /api/v1/bill-payments/reference/{reference}` | Status by external reference |
| `POST /api/v1/internal/bill-payments/provider-callback` | Provider async callback |

## Bill categories

Government, Electricity, Water, TV subscription, Airtime, Internet, Hospital, Airline.

## Provider adapters

| Adapter | Default | Notes |
|---------|---------|-------|
| `mock` | dev | Local synthetic responses |
| `selcom` | staging/prod | HTTP when `api-key` + `secret` configured; synthetic fallback on failure |

Configure via `elvo.billing.adapters.default-provider` and `ELVO_BILLING_SELCOM_*` env vars.

## Wallet settlement

Sync HTTP path (Phase 1 default):

1. Reserve wallet funds
2. Execute provider payment
3. Confirm or release reservation

Requires `ELVO_WALLET_BASE_URL` and shared `ELVO_INTERNAL_JWT_SECRET`. See [INTEGRATION-CONTRACTS.md](../../INTEGRATION-CONTRACTS.md).

## Event saga (deferred)

Async orchestrators exist for `billing.transaction.requested` ↔ `wallet.transaction.completed` but are **not wired** in the production payment path. Revisit in Phase 2 if async provider callbacks dominate.

## Tests

```powershell
cd services/elvo-billing-service
mvn test
```

Integration tests: `BillingFlowIntegrationTest`, `BillingApiIntegrationTest`.
