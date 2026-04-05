# ELVO Identity Service

## Purpose

The ELVO Identity Service is the authentication and identity core for the ELVO platform.
It is responsible for:

- User registration and login
- Token issuance and refresh
- Password reset and password update flows
- ESP/EAC verification for step-up authentication
- Fast-login challenge flows
- Session and device lifecycle management
- Internal identity/session verification APIs for other services
- Audit event publication through RabbitMQ

## Tech Stack

- Java 17
- Spring Boot 3.3.4
- Spring Security
- Spring Data JPA (PostgreSQL)
- Spring AMQP (RabbitMQ)
- Spring Data Redis
- Sentry SDK for monitoring and tracing

## Folder Structure

```text
services/elvo-identity-service/
  audit/
  cache/
  client/
  config/
  controller/
  dto/
    event/
    request/
    response/
  entity/
  exception/
  idempotency/
  mapper/
  messaging/
    consumer/
    producer/
  repository/
  saga/
  security/
  service/
    impl/
    orchestration/
  util/
  validation/
  src/
    main/
      java/com/elvo/identity/
      resources/
    test/
```

## Dependency Setup

Core dependencies are defined in [services/elvo-identity-service/pom.xml](services/elvo-identity-service/pom.xml):

- `spring-boot-starter-web`
- `spring-boot-starter-security`
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-amqp`
- `spring-boot-starter-data-redis`
- `io.jsonwebtoken` for JWT handling
- `io.sentry:sentry-spring-boot-starter-jakarta` for monitoring

Shared libraries can be enabled using profile `with-shared-libs` and property `-Delvo.shared.enabled=true`.

## Coding Conventions

- Keep layering strict: `controller -> service -> repository -> entity`.
- Use DTOs for external API contracts.
- Keep validation in DTO annotations and domain checks in services.
- Persist security-sensitive values as hashes only.
- Every meaningful user/security action should create an audit record.
- Publish audit events through `AuditEventPublisher` after persistence.
- Do not bypass correlation/request ID propagation.

## Authentication and Authorization Flows

- Public endpoints:
  - `/auth/**`
  - `/esp/**`
  - `/eac/**`
  - `/fast-login/**`
  - `/actuator/health`, `/actuator/info`
- Protected endpoints:
  - `/users/me/**` (basic auth in current implementation)

Security checks include:

- Password hash verification
- JWT access and refresh token validation
- Device trust verification for sensitive actions
- Brute-force/rate-limit protection

## API Documentation

OpenAPI specification:

- [docs/api-specs/elvo-identity-service-openapi.v1.yaml](docs/api-specs/elvo-identity-service-openapi.v1.yaml)

## Add New Features or Modules

1. Define request/response DTOs in `dto/request` and `dto/response`.
2. Add endpoint in `controller`.
3. Add business logic in `service` and implementation in `service/impl`.
4. Add repository methods if persistence changes are needed.
5. Add/extend entities only when data model changes are required.
6. Add validation annotations for input contracts.
7. Add audit persistence and event publication for critical flows.
8. Add unit, integration, and edge-case tests under `src/test`.
9. Update OpenAPI and docs in `docs/`.

## Related Docs

- [docs/architecture/elvo-identity-service-developer-guide.md](docs/architecture/elvo-identity-service-developer-guide.md)
- [docs/architecture/elvo-identity-service-deployment.md](docs/architecture/elvo-identity-service-deployment.md)
- [monitoring/sentry/README.md](monitoring/sentry/README.md)
