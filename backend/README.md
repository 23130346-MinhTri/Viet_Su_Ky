# Backend

Spring Boot 3 modular monolith baseline, targeting Java 25. The only sample
business-independent route is `GET /api/v1/health`. Spring Security is present
for later integration, but authentication is not implemented and routes are
currently public.

## Run locally

Start PostgreSQL and set `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`,
`DB_PASSWORD`, and optionally `FRONTEND_ORIGINS`. The repository-root
`.env.example` documents the local values; Maven does not load `.env` files.

```powershell
mvn spring-boot:run
```

Flyway applies versioned files under
`src/main/resources/db/migration/`. The first migration creates only the
technical `app` schema; domain tables are intentionally not included.

## Verify

```powershell
mvn clean test
mvn clean package
```
