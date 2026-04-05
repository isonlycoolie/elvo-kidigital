# ELVO Wallet Service

## Observability Quick Guide

This service exposes metrics for Prometheus, errors and traces for Sentry, and a Grafana dashboard for wallet operations.

### 1. Metrics (Prometheus)

- Endpoint: `/actuator/prometheus`
- Management exposure: `health`, `info`, `prometheus`
- Default service tag: `wallet-service`

Main custom metrics:

- `wallet_transactions_total`
  - Tags: `service`, `flow`, `outcome`
- `wallet_balance_change_amount`
  - Tags: `service`, `flow`, `direction`
- `wallet_reservations_total`
  - Tags: `service`, `action`, `outcome`
- `wallet_freeze_actions_total`
  - Tags: `service`, `action`, `outcome`
- `wallet_saga_compensations_total`
  - Tags: `service`, `flow`
- `wallet_event_publish_total`
  - Tags: `service`, `event_type`, `outcome`

Scrape fragment:

- `monitoring/prometheus/elvo-wallet-service-scrape.yml`

### 2. Error Tracking (Sentry)

Configured via Spring properties and environment variables.

Required/important variables:

- `SENTRY_DSN`
- `SENTRY_ENVIRONMENT`
- `SENTRY_RELEASE`
- `SENTRY_TRACES_SAMPLE_RATE`
- `ELVO_SERVICE_TAG`

Behavior:

- Critical and unhandled exceptions are captured with tags and request context.
- Request identifiers (`requestId`, `correlationId`) and user context (`X-User-Id` when present) are attached.

### 3. Dashboard (Grafana)

Dashboard file:

- `monitoring/grafana/elvo-wallet-service-dashboard.json`

Current panels include:

- Wallet Transaction Rate
- Transaction Failure Ratio
- Reservation Operations
- Freeze Actions
- Balance Change Distribution
- Saga Compensations
- Event Publish Outcomes

### 4. Local Validation

From service directory:

```powershell
mvn test
```

Then verify metrics endpoint when service is running:

```powershell
curl http://localhost:8080/actuator/prometheus
```

### 5. Notes

- The service tag is controlled by `ELVO_SERVICE_TAG` and defaults to `wallet-service`.
- Prometheus and Grafana assets are in repository-level `monitoring/` for shared operations usage.
