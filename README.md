# skillswap-be

Backend service for SkillSwap, a Spring Boot modular monolith for mentor matching, booking, identity, catalog, feedback, file storage, notification, and admin workflows.

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
git clone https://github.com/QuangTam2005/skillswap-be.git
cd skillswap-be
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

Health endpoint:

```text
http://localhost:8080/actuator/health
```

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

FLYWAY_ENABLED=false
HIBERNATE_DDL_AUTO=update
```

For local development, `application-dev.yml` provides safe defaults for PostgreSQL and JWT so the project can run quickly after cloning.

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

- `POST /api/v1/auth/google`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `GET /api/v1/auth/me`

Access tokens are JWTs. Refresh tokens are randomly generated, hashed with SHA-256, and stored in `user_sessions`.

Google login requires `GOOGLE_CLIENT_ID` outside local dev/test overrides. The service verifies the Google token audience before issuing local tokens.

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
docker compose up --build spring-backend
```

Stop services:

```powershell
docker compose down
```

Reset local database volume:

```powershell
docker compose down -v
docker compose up -d postgres-db
```

## Git Workflow

Recommended branch flow:

- `main`: stable base
- `dev`: active integration branch
- feature branches: `feature/<short-name>`

Before opening a pull request:

```powershell
.\mvnw.cmd test
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
