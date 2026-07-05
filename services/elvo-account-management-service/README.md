
# ELVO Account Management Service

## Purpose

System of record for account structures, lifecycle state, permissions, limits, relationships, and restrictions.

## Scope

- Account creation and lookup (by ID, user, EAN)
- Pre-flight validation for transfer, withdrawal, and receive
- Limit and permission checks; maker-checker workflows
- Account lifecycle: activate, freeze, suspend, close, reopen, archive
- **Post-verification sync** from identity (`POST /api/v1/internal/accounts/sync-verification`)
- Identity registration event consumer (RabbitMQ)

## Integration

| Caller | Flow |
|--------|------|
| Identity (event) | Registration → create account (PENDING, UNVERIFIED) |
| Identity (HTTP) | Post-verification → sync KYC + ACTIVE |
| Wallet (HTTP) | validate-transfer, validate-withdrawal, check-limit |

Internal APIs require shared **internal JWT**. See [INTEGRATION-CONTRACTS.md](../../INTEGRATION-CONTRACTS.md).

## Internal API base path

`/api/v1/internal/accounts`

## Tech stack

Java 17, Spring Boot 3.3.4, PostgreSQL, RabbitMQ, Flyway.
