# Sentry Monitoring Baseline

This directory documents the baseline Sentry integration pattern for ELVO services.

## Required Environment Variables

- `SENTRY_DSN`
- `SENTRY_ENVIRONMENT`
- `SENTRY_RELEASE`
- `SENTRY_TRACES_SAMPLE_RATE`
- `SENTRY_AUTH_TOKEN` (for source context upload during CI build)
- `SENTRY_ORG`
- `SENTRY_PROJECT`
- `SENTRY_URL` (optional, defaults to `https://sentry.io/`)

## Service Tag Convention

Use the service identifier as the primary tag:

- `elvo-identity-service`
- `elvo-wallet-service`
- `elvo-agent-service`
- `elvo-billing-service`
- `elvo-delegated-access-service`
- `elvo-notification-service`
- `elvo-web-dashboard-service`

## Source Context Upload

Source context upload is configured through the Maven Sentry plugin in each service module. CI should export `SENTRY_AUTH_TOKEN`, `SENTRY_ORG`, and `SENTRY_PROJECT` before build.

## Tracing Guidance

`SENTRY_TRACES_SAMPLE_RATE` defaults to `1.0` in development and should be tuned per environment in deployment configuration.
