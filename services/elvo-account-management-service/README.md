# ELVO Account Management Service

## Purpose

The ELVO Account Management Service is the system of record for account structures, lifecycle state, permissions, limits, relationships, and restrictions.

This initial branch-only implementation introduces the service boundary, schema, and internal contract surface without changing the stable identity or wallet runtime behavior yet.

## Initial Scope

- Account creation and lookup
- EAN generation
- Account lifecycle transitions
- Permission and limit checks
- Restriction records
- Audit log persistence
- Internal REST contract for wallet and identity integration

## Tech Stack

- Java 17
- Spring Boot 3.3.4
- Spring Security
- Spring Data JPA
- Spring AMQP
- PostgreSQL
- Sentry SDK

## Folder Structure

```text
services/elvo-account-management-service/
  src/
    main/
      java/com/elvo/accountmanagement/
      resources/
        db/migration/
    test/
```

## Internal API Surface

- `POST /api/v1/internal/accounts`
- `GET /api/v1/internal/accounts/{accountId}`
- `GET /api/v1/internal/accounts/user/{userId}`
- `GET /api/v1/internal/accounts/ean/{ean}`
- `POST /api/v1/internal/accounts/validate-transfer`
- `POST /api/v1/internal/accounts/validate-withdrawal`
- `POST /api/v1/internal/accounts/validate-receive`
- `POST /api/v1/internal/accounts/check-limit`
- `POST /api/v1/internal/accounts/check-permission`
- `POST /api/v1/internal/accounts/freeze`
- `POST /api/v1/internal/accounts/unfreeze`
- `POST /api/v1/internal/accounts/suspend`
- `POST /api/v1/internal/accounts/close`
- `POST /api/v1/internal/accounts/reopen`
- `POST /api/v1/internal/accounts/archive`
- `POST /api/v1/internal/accounts/restrict`
- `POST /api/v1/internal/accounts/remove-restriction`

## Notes

- Identity and wallet remain unchanged in this slice.
- The next phase will wire the identity registration event and wallet validation calls to this service.
