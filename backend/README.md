# Backend

Spring Boot 3 modular monolith targeting Java 17. It provides account
registration/login, signed JWT authentication, player profiles, and
`GET /api/v1/health`.

## Run locally

Start PostgreSQL and set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
`DB_PASSWORD`, `JWT_SECRET` (generated, at least 32 characters), and optionally
`FRONTEND_ORIGINS`. The repository-root `.env.example` lists variable names;
Maven does not load `.env` files. No default JWT secret is configured.

```powershell
mvn spring-boot:run
```

Flyway applies versioned files under
`src/main/resources/db/migration/`. Migrations create the application schema,
accounts, and player profiles.

## Verify

```powershell
mvn clean test
mvn clean package
```
