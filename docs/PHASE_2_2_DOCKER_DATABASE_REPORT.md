# Phase 2.2: Docker and Database Integration Report

**Date:** 2026-11-07  
**Project:** VIỆT SỬ KÝ  
**Overall status:** **BLOCKED — Phase 2.2 is not complete.** Docker CLI and Compose are installed and Compose configuration is valid, but no Docker Engine endpoint is available. No containers were started, so the database, Flyway, backend, frontend, and Nginx runtime acceptance criteria are not verified.

## Environment

| Component | Observed | Status |
|---|---|---|
| OS | Windows 11, amd64 | Observed |
| Docker CLI | 29.3.1 | Available |
| Docker Compose | v5.1.1 | Available |
| Active Docker context | `desktop-linux` | Points to unavailable Docker Desktop engine |
| Alternate context | `default` | Also points to an unavailable named pipe |
| Project Java target | 17 | Confirmed in backend POM and Dockerfile |
| Spring Boot | 3.5.3 | Confirmed in backend POM |
| PostgreSQL image configured | `postgres:16-alpine` | Configuration only; image/container version not runtime-verified |
| Flyway | 11.8.2 | Configured in backend POM |

No credential values were read into this report or emitted in command output.

## Task A — Docker Diagnostics

- `docker version` displayed the client but failed to contact the server through `npipe:////./pipe/dockerDesktopLinuxEngine`.
- `docker info` failed with the same missing named-pipe error.
- Active context is `desktop-linux`; its endpoint is `npipe:////./pipe/dockerDesktopLinuxEngine`.
- The `default` context endpoint, `npipe:////./pipe/docker_engine`, also failed when queried explicitly.
- `docker desktop status` reported Docker Desktop is not running and suggested starting it.
- No `Docker Desktop` process or `com.docker.service` service was present in the environment check.
- Docker CLI and Compose are installed. The observed root cause is that the local Docker Desktop engine/service is not running (or is unavailable), not a demonstrated Compose configuration or context-selection issue.
- **No Docker context was changed.** The Desktop engine cannot be started from this session without an available Docker Desktop service/application.

## Task B — Compose Configuration

`docker compose config --quiet`: **PASS**. `docker compose config --services` resolved `database`, `backend`, and `frontend`; `docker compose config --volumes` resolved `postgres_data`.

| Service | Image/build | Ports | Storage/network | Environment and startup dependency | Health check |
|---|---|---|---|---|---|
| `database` | `postgres:16-alpine` | `${DB_PORT:-5432}:5432` | Persistent named volume `postgres_data` at `/var/lib/postgresql/data`; Compose default project network | Requires `POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` | `pg_isready` using configured user/database; 5s interval, 5s timeout, 10 retries, 10s start period |
| `backend` | Build from `./backend/Dockerfile` | `${BACKEND_PORT:-8080}:8080` | Compose default project network; connects to host `database` on port 5432 | Defaults active Spring profile to `prod`; database name/user/password supplied from Compose environment; waits for `database` with `service_healthy` | Requests `/actuator/health` on port 8080; 10s interval, 5s timeout, 12 retries, 30s start period |
| `frontend` | Build from repository root using `frontend/Dockerfile` | `${FRONTEND_PORT:-8081}:80` | Compose default project network; Nginx proxies to `backend:8080` | Build arg `VITE_API_BASE_URL` defaults to `/api/v1`; waits for `backend` with `service_healthy` | Requests `/` on port 80; 10s interval, 5s timeout, 5 retries, 5s start period |

Compose defines no explicit `networks` section, so services use the default project network and service-name DNS. No Compose setting removes or resets the persistent database volume.

## Tasks C–F — Runtime Integration

Static configuration and migration files were inspected, but runtime acceptance cannot be claimed without a working engine:

- **PostgreSQL container / PostgreSQL 16 / health:** NOT VERIFIED. The configured image tag is `postgres:16-alpine`; no image/container was started or inspected.
- **Database connection and app schema:** NOT VERIFIED. The local PostgreSQL port 5433 did not accept a TCP connection during the environment check.
- **Flyway:** Configuration enables migrations from `classpath:db/migration`, with schema `app`, schema creation enabled, and `baseline-on-migrate` disabled. `V1__create_app_schema.sql` contains `CREATE SCHEMA IF NOT EXISTS app;`. Whether it executed and whether `flyway_schema_history` exists are NOT VERIFIED.
- **Backend/JPA/Actuator/restarts:** Datasource uses `database:5432` in Compose; JPA uses `ddl-auto: validate`; the health check targets `/actuator/health`. No backend container or database connection was available to verify startup, schema validation, HTTP status, or restart count.
- **Frontend/Nginx/API proxy/SPA fallback:** Static Nginx configuration proxies `/api/` to `http://backend:8080` and uses `try_files ... /index.html` for SPA fallback. Container health, API proxy responses, and browser-route fallback are NOT VERIFIED.

No migration was edited or run, and no database state was changed.

## Acceptance Criteria

| # | Criterion | Result | Evidence |
|---:|---|---|---|
| 1 | Docker Engine operational | **BLOCKED** | Both configured endpoints fail; Docker Desktop reports not running. |
| 2 | PostgreSQL container healthy | **NOT VERIFIED** | Could not start containers. |
| 3 | PostgreSQL accepts connections | **NOT VERIFIED** | No PostgreSQL container; local port 5433 refused TCP connection. |
| 4 | Backend connects to PostgreSQL | **NOT VERIFIED** | Backend container could not start. |
| 5 | Flyway V1 present in migration history | **NOT VERIFIED** | Database and `flyway_schema_history` could not be inspected. |
| 6 | Backend health returns HTTP 200 | **NOT VERIFIED** | Backend container could not start. |
| 7 | Frontend container healthy | **NOT VERIFIED** | Frontend container could not start. |
| 8 | Nginx API reverse proxy works | **NOT VERIFIED** | Nginx and backend were not running. |
| 9 | Containers do not continuously restart | **NOT VERIFIED** | No containers were created. |

## Problems Discovered

1. Docker Desktop is not running or otherwise unavailable; the active and alternate named-pipe endpoints are both missing.
2. Because the engine is unavailable, no Docker image builds, PostgreSQL checks, Flyway migration execution, backend startup, frontend startup, or container restart checks can be performed.
3. Compose ports and health conditions validate statically, but successful configuration parsing does not prove runtime health.

## Fixes Applied

- Updated `docs/SETUP_AUDIT_REPORT.md` with the Phase 2.2 diagnostic result and explicit runtime limitations.
- No Docker configuration, application configuration, dependency, migration, database, volume, or source-code changes were made.
- No Docker context was switched; no container or volume was modified.

## Remaining Risks and Next Action

Phase 2.2 remains incomplete until Docker Desktop/Engine is available. After the engine is started, rerun the PostgreSQL, Flyway, backend, frontend, Nginx, health, and restart checks from the acceptance table. Preserve `postgres_data`; do not reset or remove it. Only report PASS for an item after collecting runtime evidence.
