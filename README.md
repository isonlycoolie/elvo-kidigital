# elvo-digital

Monorepo scaffold for Elvo services, shared libraries, infrastructure, monitoring, and documentation.

## Local Runtime Secret Handling

- Never commit real secrets. Runtime secret files are intentionally ignored by git:
	- `.env`
	- `.env.shared`
	- `services/*/.env`
- Keep only template files committed:
	- `.env.example`
	- `.env.shared.example`
	- `services/*/.env.example`
- For local runs, create local secret files from templates and inject real values only on your machine.
- Before pushing, verify that no runtime secret files are staged.

## Account Management Service Docs

- Service README: [services/elvo-account-management-service/README.md](services/elvo-account-management-service/README.md)
- Implementation plan: [docs/architecture/elvo-account-management-service-implementation-plan.md](docs/architecture/elvo-account-management-service-implementation-plan.md)

## Identity Service Docs

- Service README: [services/elvo-identity-service/README.md](services/elvo-identity-service/README.md)
- OpenAPI spec: [docs/api-specs/elvo-identity-service-openapi.v1.yaml](docs/api-specs/elvo-identity-service-openapi.v1.yaml)
- Developer guide: [docs/architecture/elvo-identity-service-developer-guide.md](docs/architecture/elvo-identity-service-developer-guide.md)
- Deployment guide: [docs/architecture/elvo-identity-service-deployment.md](docs/architecture/elvo-identity-service-deployment.md)
- Sentry baseline: [monitoring/sentry/README.md](monitoring/sentry/README.md)
