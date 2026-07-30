# SkillSwap Backend

SkillSwap is a Spring Boot modular monolith for FPTU mentoring: mentor discovery,
service availability, booking, payment, direct booking chat, forum, blog, and
notifications.

## Runtime

- Java 21, Spring Boot 3.2.5, Spring Modulith
- PostgreSQL with Flyway migrations
- RabbitMQ for asynchronous delivery and realtime relay
- Caffeine for bounded single-instance caches
- Cloudflare R2/S3-compatible storage for public and private objects
- Google OAuth and JWT access tokens with an HttpOnly refresh cookie
- PayOS payment integration

The source of truth is the Java controller/DTO/service code and Flyway
migrations. Frontend documentation is an integration contract, not a replacement
for runtime validation.

## Local development

```powershell
copy .env.example .env
docker compose up -d postgres-db rabbitmq
.\mvnw.cmd test
.\mvnw.cmd spring-boot:run
```

Local API: `http://localhost:8080`.

## Verification

```powershell
.\mvnw.cmd clean verify
docker compose config
```

The CI pipeline verifies tests, security scanning, release preflight, migration
rollout headers, immutable image publishing, and production deployment.

## Documentation

- [Frontend API contracts](docs/frontend-api-guide/00-overview.md)
- [Single-VPS operations](docs/operations.md)
- [.env template](.env.example)

## Repository layout

```text
src/main/java/com/fptu/exe/skillswap/
  infrastructure/  Runtime adapters, security, storage, realtime wiring
  modules/         Business modules
  shared/          Shared API, exception, persistence and utility code
src/main/resources/db/migration/
  Flyway schema migrations
ops/
  Backup, restore, smoke-test and release scripts
```
