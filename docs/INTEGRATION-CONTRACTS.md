# ELVO Integration Contracts

Cross-service URL and authentication conventions for the ELVO Digital platform.

## Base URL convention

**Rule:** Base URL = `scheme://host:port` only. Full API paths are appended by clients.

| Caller | Target | Base URL (Docker) | Path prefix |
|--------|--------|-------------------|-------------|
| identity | wallet | `http://elvo-wallet-service:8082` | `/api/v1/internal/wallets/{userId}` |
| identity | account-mgmt | `http://elvo-account-management-service:8084` | `/api/v1/internal/accounts/...` |
| billing | wallet | `http://elvo-wallet-service:8082` | `/api/v1/internal/wallets/{userId}/...` |
| wallet | account-mgmt | `http://elvo-account-management-service:8084` | `/api/v1/internal/accounts/...` |
| wallet | identity | `http://elvo-identity-service:8081` | `/internal/...` |

**Do not** append `/internal` to base URLs when client code already includes `/api/v1/internal/...`.

## Internal service JWT

All service-to-service HTTP calls to wallet and account-mgmt internal APIs use **signed HS256 JWT**:

| Claim | Value |
|-------|-------|
| `iss` | `ELVO_INTERNAL_JWT_ISSUER` (default: `elvo-wallet-service-internal-dev`) |
| `aud` | `ELVO_INTERNAL_JWT_AUDIENCE` (same as issuer for local) |
| `roles` | `["INTERNAL_SERVICE"]` |
| `sourceService` | Calling service name (e.g. `identity-service`, `billing-service`) |
| `serviceIdentity` | Must match `sourceService` |
| Header | `Authorization: Bearer <jwt>`, `X-Source-Service: <sourceService>` |

Shared secret: `ELVO_INTERNAL_JWT_SECRET` (min 32 bytes).

## Local Docker profile

For local development, disable TLS on infra and inter-service HTTP:

```env
ELVO_DB_SSL_ENABLED=false
ELVO_REDIS_SSL_ENABLED=false
ELVO_RABBITMQ_SSL_ENABLED=false
ELVO_IDENTITY_TLS_ENFORCE_HTTPS=false
ELVO_IDENTITY_TLS_ENFORCE_MTLS=false
```

Wallet to account-mgmt URL must use `http://`, not `https://`.

## Post-verification lifecycle

After identity OTP verification:

1. `POST /api/v1/internal/wallets/{userId}`: create wallet (identity to wallet)
2. `POST /api/v1/internal/accounts/sync-verification`: upgrade KYC and activate account (identity to account-mgmt)

Profile provisioning to web-dashboard is **disabled** until that service ships.

## Billing wallet settlement

Sync HTTP path (Phase 1 default):

1. `POST .../reserve`: hold funds
2. Provider adapter pay
3. `POST .../confirm-debit` or `.../release` on success/failure

Event saga (`billing.transaction.requested` and `wallet.transaction.completed`) is **deferred** to Phase 2.

## Public customer API areas

| Service | Public routes | Purpose |
|---------|---------------|---------|
| Identity | `/auth/**`, `/users/me/**` | Registration, login, MFA, profile |
| Wallet | `/wallets/**` | Balance, send, withdraw, history |
| Billing | `/api/v1/bill-payments/**` | Lookup, pay, status |

Internal settlement and policy routes use `/api/v1/internal/**` and require the service JWT above.

## Environment variables (shared)

See [`.env.shared.example`](../.env.shared.example) for `ELVO_INTERNAL_JWT_*`, `ELVO_WALLET_BASE_URL`, and SSL overrides.

## Related documentation

- [Platform roadmap](./PLATFORM-ROADMAP.md)
- [Event catalog](./EVENT-CATALOG.md)
- [Sequence flows](./SEQUENCE-FLOWS.md)
