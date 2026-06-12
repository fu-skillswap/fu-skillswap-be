# skillswap-be

Backend service for FU SkillSwap, a Spring Boot modular monolith for mentoring between FPT University students and alumni. The project provides identity, academic onboarding, mentor profile onboarding, catalog, booking, feedback, file storage, notification, and admin foundations.

## Tech Stack

- Java 21
- Spring Boot 3.2.5
- Spring Modulith
- Spring Web
- Spring Security
- Spring Data JPA
- PostgreSQL
- H2 for tests
- JWT authentication
- Google OAuth login
- Cloudinary integration, optional
- Maven Wrapper
- Docker Compose

## Requirements

- JDK 21
- Docker Desktop
- Git

You do not need to install Maven globally. Use the included Maven wrapper:

```powershell
.\mvnw.cmd
```

## Quick Start

Clone the repository:

```powershell
git clone -b dev git@github.com:fu-skillswap/fu-skillswap-be.git
cd fu-skillswap-be
```

Create local environment file:

```powershell
copy .env.example .env
```

Start PostgreSQL:

```powershell
docker compose up -d postgres-db
```

Run tests:

```powershell
.\mvnw.cmd test
```

Run the application:

```powershell
.\mvnw.cmd spring-boot:run
```

The API runs on:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

Public smoke-test endpoint:

```text
http://localhost:8080/api/campuses
```

Note: `/actuator/health` is currently protected by Spring Security. Use a public catalog endpoint for simple unauthenticated smoke tests unless the security config is changed later.

## Environment Variables

Use `.env.example` as the template. Never commit `.env`.

Important variables:

```text
DATABASE_URL=jdbc:postgresql://localhost:5444/skillswapdb
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=123456

JWT_SECRET_KEY=replace-with-a-base64-encoded-secret-at-least-32-bytes
JWT_EXPIRATION=3600000
JWT_REFRESH_EXPIRATION=604800000

GOOGLE_CLIENT_ID=
CORS_ALLOWED_ORIGIN_PATTERNS=http://localhost:3000,http://localhost:5173,http://localhost:8080

CLOUDINARY_ENABLED=false
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=

R2_ENABLED=false
R2_ENDPOINT=
R2_ACCESS_KEY_ID=
R2_SECRET_ACCESS_KEY=
R2_BUCKET=
R2_REGION=auto
R2_DOCUMENTS_PREFIX=skillswap/verification-documents

FLYWAY_ENABLED=false
HIBERNATE_DDL_AUTO=update
```

For Docker Compose, these variables must not be blank:

```text
JWT_SECRET_KEY
JWT_EXPIRATION
JWT_REFRESH_EXPIRATION
GOOGLE_CLIENT_ID
CORS_ALLOWED_ORIGIN_PATTERNS
```

If mentor verification uploads are enabled in a deployed environment:

```text
CLOUDINARY_ENABLED=true
CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET

R2_ENABLED=true
R2_ENDPOINT
R2_ACCESS_KEY_ID
R2_SECRET_ACCESS_KEY
R2_BUCKET
```

If a variable is defined as an empty string in `.env`, it overrides the default in `application.yaml`. For example, `JWT_EXPIRATION=` will break startup because Spring cannot bind an empty string to `long`.

## Profiles

### dev

Default local profile.

- PostgreSQL on port `5444`
- `ddl-auto: create`
- SQL logging enabled
- Intended for clean development schema generation

Because the project uses UUID v7 primary keys, the dev schema should be recreated from a clean database after major entity changes.

### test

Used by Maven tests.

- H2 in-memory database
- `ddl-auto: create-drop`
- Flyway disabled

### prod

Production profile.

- Reads database settings from environment variables
- Uses `HIBERNATE_DDL_AUTO`, default `update` for current MVP stage
- Flyway disabled by default until full DDL migrations are added

When production migrations are introduced, set:

```text
HIBERNATE_DDL_AUTO=validate
FLYWAY_ENABLED=true
```

## Project Structure

```text
src/main/java/com/fptu/exe/skillswap
├── ProjectApplication.java
├── infrastructure
│   ├── config
│   ├── filter
│   ├── security
│   └── storage
├── modules
│   ├── academic
│   ├── admin
│   ├── booking
│   ├── catalog
│   ├── feedback
│   ├── filestorage
│   ├── identity
│   ├── matching
│   ├── mentor
│   └── notification
└── shared
    ├── constant
    ├── controller
    ├── dto
    ├── entity
    ├── exception
    ├── persistence
    └── util
```

## Architecture Notes

The project uses Spring Modulith. Each package under `modules` represents a business module. Shared infrastructure and cross-cutting code live under:

- `shared`: response model, exceptions, base entity, UUID v7, utilities
- `infrastructure`: security, config, filters, storage integration

Run this test to verify module boundaries:

```powershell
.\mvnw.cmd -Dtest=ModulithTest test
```

## Authentication

Current auth flow:

- `POST /api/auth/google`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

Access tokens are JWTs. Refresh tokens are randomly generated, hashed with SHA-256, and stored in `user_sessions`.

The frontend obtains a Google `idToken` via Google Identity Services, sends it to `POST /api/auth/google`, receives `accessToken` and `refreshToken`, then calls authenticated APIs with:

```text
Authorization: Bearer <accessToken>
```

Google login requires `GOOGLE_CLIENT_ID`. The service verifies the Google token audience before issuing local tokens.

## Current APIs

### Academic Catalog

- `GET /api/campuses`
- `GET /api/academic-programs`
- `GET /api/specializations`
- `GET /api/academic-programs/{programId}/specializations`

### Student Profile

- `GET /api/me/student-profile`
- `PUT /api/me/student-profile`

### Mentor Profile Onboarding

- `GET /api/me/mentor-profile`
- `PUT /api/me/mentor-profile/basic`
- `PUT /api/me/mentor-profile/expertise`

Mentor profile onboarding is split for frontend UX:

- Step 1: Basic Profile
- Step 2: Expertise
- Step 3: Pricing & Availability, planned
- Step 4: Services, planned and skippable

`GET /api/me/mentor-profile` returns `exists=false` when a user has not created a mentor profile yet, so the frontend can start onboarding without treating it as an error.

## ID Convention

All entity primary keys use UUID v7.

Rules:

- Do not introduce `Long id` for entities.
- Do not add a separate `publicId` unless there is a strong reason.
- Public API identifiers should use the entity UUID.
- Use `@GeneratedUuidV7` for generated UUID v7 primary keys.

The generator lives in:

```text
shared/persistence
```

## API Response Format

Successful responses use:

```json
{
  "timestamp": "2026-06-09 20:00:00",
  "status": 200,
  "code": "SUCCESS_0200",
  "message": "success",
  "data": {}
}
```

Errors are handled by `GlobalExceptionHandler` and returned with the same response shape.

## Useful Commands

Run tests:

```powershell
.\mvnw.cmd test
```

Build jar:

```powershell
.\mvnw.cmd package
```

Build without tests:

```powershell
.\mvnw.cmd package -DskipTests
```

Start database:

```powershell
docker compose up -d postgres-db
```

Start backend with Docker Compose:

```powershell
docker compose up -d --build
```

Stop services:

```powershell
docker compose down
```

Reset local database volume:

```powershell
docker compose down -v
docker compose up -d --build
```

Smoke test:

```powershell
curl http://localhost:8080/api/campuses
```

## Git Workflow

Branch flow:

- `main`: stable base
- `dev`: active integration branch
- `feat/<short-name>`: new feature
- `fix/<short-name>`: bug fix for dev or staging
- `hotfix/<short-name>`: urgent production fix
- `refactor/<short-name>`: code restructuring without behavior change
- `docs/<short-name>`: documentation only
- `chore/<short-name>`: build, config, dependency, or maintenance work

Commit message format:

```text
<type>(<scope>): <description>
```

Examples:

```text
feat(mentor): add mentor profile onboarding APIs
chore(config): update docker compose environment
```

Rules:

- Use lowercase description.
- Use present-tense verb phrase.
- Do not end with a period.
- Keep `type` aligned with the branch prefix.

Before opening a pull request:

```powershell
.\mvnw.cmd test
```

## Test Deployment On VPS

The current test deployment branch is `dev`.

```bash
cd /opt/fu-skillswap/backend
git fetch origin
git checkout dev
git pull origin dev
docker compose config
docker compose down -v
docker compose up -d --build
docker logs -f skillswap-backend
```

`docker compose down -v` deletes the PostgreSQL volume and all database data. Use it only for test environments where a clean schema is expected.

After startup, smoke test:

```bash
curl http://localhost:8080/api/campuses
```

## Do Not Commit

These are ignored and should not be committed:

- `.env`
- `target/`
- `uploads/`
- `logs/`
- IDE local metadata

## Troubleshooting

### App cannot connect to PostgreSQL

Check Docker:

```powershell
docker compose ps
```

Restart DB:

```powershell
docker compose up -d postgres-db
```

### Schema mismatch after entity changes

For local dev, reset the database volume:

```powershell
docker compose down -v
docker compose up -d postgres-db
```

Then run the app again.

### Google login fails

Check:

- `GOOGLE_CLIENT_ID`
- The frontend is sending a valid Google ID token
- The token audience matches `GOOGLE_CLIENT_ID`

### Cloudinary fails on startup

If `CLOUDINARY_ENABLED=true`, all of these must be set:

```text
CLOUDINARY_CLOUD_NAME
CLOUDINARY_API_KEY
CLOUDINARY_API_SECRET
```
