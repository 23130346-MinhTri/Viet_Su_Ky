# VIỆT SỬ KÝ Setup Audit Report

**Audit date:** 2026-11-05  
**Java 17 migration update:** 2026-11-07; see [JAVA_17_MIGRATION_REPORT.md](JAVA_17_MIGRATION_REPORT.md) for migration-specific evidence.  
**Phase 2.2 update:** 2026-11-07; Compose configuration validates, but Docker Engine is unavailable, so no database or container runtime integration was executed. See [PHASE_2_2_DOCKER_DATABASE_REPORT.md](PHASE_2_2_DOCKER_DATABASE_REPORT.md).  
**Scope:** Initial repository setup, build checks, configuration review, and available local runtime checks.  
**Result:** Frontend checks pass. The backend now targets Java 17, and `mvn clean test package` passes using Maven running on JDK 17. Compose configuration passes validation, but Docker Engine is unavailable; PostgreSQL, Flyway, and container runtime integration remain unverified.

## 1. Project Structure

```text
frontend/                 React, TypeScript, Vite, Phaser, SCSS
  src/
    App.tsx               React Router and route composition
    layouts/              Shared application layout
    pages/                Home and demo game pages
    services/             Axios API client
    game/                 Phaser component, config, and boot scene
backend/                  Spring Boot modular-monolith starter
  src/main/java/          Application, health API, and config
  src/main/resources/     Spring profiles and Flyway migrations
  src/test/java/          Backend tests
database/                 Database setup notes
infrastructure/nginx/     Nginx reverse proxy and SPA fallback
docker-compose.yml        PostgreSQL, backend, frontend services
viet-su-ky-ai-docs/       Product, architecture, and security guidance
docs/                     Operational documentation and this audit
```

The current backend is a bootstrap skeleton: it contains the health endpoint and configuration packages, but no domain modules, entities, or gameplay APIs.

## 2. Environment and Dependency Versions

| Area | Configured or installed version | Evidence / notes |
|---|---|---|
| Node.js | 24.14.1 | Local runtime |
| npm | 11.11.0 | Local package manager |
| React / React DOM | 19.3.1 | `frontend/package.json` |
| React Router | 7.18.5 | `frontend/package.json` |
| TypeScript | 6.0.3 | `frontend/package.json` |
| Vite | 8.3.5 | Production build output |
| Phaser | 3.90.0 | `frontend/package.json` |
| Axios | 1.21.0 | `frontend/package.json` |
| Java installed | 21.0.10 and 17.0.16 | JDK 17 is selected for Maven through `JAVA_HOME` |
| Java project target | 17 | `backend/pom.xml`, both backend Docker stages, and setup documentation |
| Spring Boot | 3.5.3 | `backend/pom.xml` |
| Maven | 3.9.15 | Successful verification used Maven running on JDK 17.0.16 |
| Flyway | 11.8.2 | `backend/pom.xml` |
| PostgreSQL | 16 Alpine image configured | `docker-compose.yml`; no running server version could be confirmed |
| Docker / Compose | CLI 29.3.1 / 5.1.1 | Docker daemon is unavailable |
| Nginx | 1.29 Alpine image configured | `frontend/Dockerfile` |

**Migration update:** The project is standardized on Java 17. Maven verification explicitly selected the installed JDK 17 for the process.

## 3. Frontend Test and Build Results

Commands were run from `frontend/`:

| Command | Result |
|---|---|
| `npm run typecheck` | PASS |
| `npm run lint` | PASS |
| `npm run build` | PASS; Vite transformed 90 modules |
| Automated frontend tests | Not available; no test script or frontend test suite is configured |

The production build emitted a chunk-size warning: the lazy game page chunk is **1,195.50 kB** (319.17 kB gzip), above Vite's 500 kB warning threshold. The Phaser route is lazy-loaded, so the chunk is separated from the initial app bundle, but it remains large when the game route loads.

Static frontend review:

- React Router is configured with `BrowserRouter`, nested routes, and lazy loading for `/game`.
- Axios uses the configurable `VITE_API_BASE_URL`, a 10-second timeout, and JSON accept header. Vite proxies `/api` to the configured development backend URL.
- `PhaserGame` creates one Phaser instance in an effect and destroys it with `game.destroy(true)` on unmount. `React.StrictMode` is enabled; browser runtime behavior was not exercised during this audit.
- Unknown paths currently render the home page; a dedicated not-found page is not part of this starter.

## 4. Backend Test and Build Results

After the Java target was migrated, the build was run from `backend/` with Maven using JDK 17:

```text
mvn clean test package
JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot
Maven: 3.9.15, Java: 17.0.16
Result: BUILD SUCCESS
Tests: 1 run, 0 failures, 0 errors, 0 skipped
```

Compilation reported `release 17` for both production and test sources. The only test is `HealthControllerTest`; it checks the health response with security filters disabled and does not cover database connectivity, CORS, validation, exception handling, or business behavior.

## 5. Database and Migration Results

- Compose configuration validation (`docker compose config --quiet`) passed.
- Docker daemon check failed: the Docker Desktop Linux engine pipe was unavailable. `docker compose ps` therefore could not inspect or start services.
- PostgreSQL connection was not established. A backend startup attempt using the local environment configuration failed while Flyway attempted to connect to the configured local database endpoint (`localhost:5433`); the connection was refused.
- Because the application could not reach PostgreSQL, **Flyway execution and migration state were not verified**.
- Static review found `V1__create_app_schema.sql` creates only the `app` schema. No tables or domain entities currently exist, so entity-to-migration consistency cannot yet be assessed.
- JPA is configured with `ddl-auto: validate`, and Flyway is enabled with schema creation enabled. The database password is supplied through `DB_PASSWORD` rather than embedded in the application configuration. The root `.env` is ignored by Git; no secret values are included in this report.
- `docker-compose.yml` configures PostgreSQL 16 with a `pg_isready` health check and gates backend startup on database health.

## 6. Docker and Nginx Results

**Static checks:**

- `docker compose config --quiet`: PASS.
- Service dependencies are ordered by health: backend waits for PostgreSQL, and frontend waits for backend.
- Compose defines health checks for PostgreSQL, the Spring Actuator endpoint, and the Nginx-served frontend.
- The backend image uses Maven/Temurin 17 for the build stage and Temurin 17 JRE for runtime, consistent with the POM target.
- The frontend image uses Node 24 to build and Nginx 1.29 Alpine to serve the built assets.
- Nginx proxies `/api/` to the Compose backend service and falls back to `index.html` for SPA routes.

**Runtime checks:** Docker image builds, service health checks, inter-service networking, and Nginx proxy behavior could not be exercised because the Docker daemon was unavailable. The configured Nginx and health-check behavior is therefore a static review, not a runtime pass.

## 7. Architecture Review

| Rule | Finding |
|---|---|
| React manages application UI and routing | Confirmed in the current frontend structure and `App.tsx`. |
| Phaser manages game rendering and scenes | Confirmed for the demo: Phaser is isolated in `PhaserGame` and `BootScene`; React owns the page and lifecycle. No map/gameplay features exist yet. |
| Backend is authoritative for scores, rewards, mission completion, unlocks, and persistent progress | Present as an architecture/product rule in project documentation, but not verifiable as runtime behavior: those endpoints and domain models do not exist yet. |
| Frontend must not determine final rewards or scores | No score or reward calculation exists in the frontend. The future rule is documented, but must be enforced by server-side APIs when those features are designed. |
| Modular backend architecture | The project is described as a modular monolith, but the current implementation only has `common` and `config` packages. Domain boundaries and module dependency rules are not implemented yet. |

Additional backend configuration observations:

- CORS is wired into Spring Security, reads allowed origins from `FRONTEND_ORIGINS`, permits the configured API methods/headers, and does not allow credentials.
- There are no current request DTOs or validation annotations to exercise the Validation dependency.
- No global exception handler or domain error response format is present. There are also no business endpoints for which to validate those behaviors.
- Security currently disables CSRF and permits all requests. The repository documentation identifies authentication as unimplemented; this is acceptable only for the starter scope and must change before protected or state-changing features are exposed.

## 8. Problems Discovered

1. The backend requires Java 17 for local builds; the migration and verification details are recorded in [JAVA_17_MIGRATION_REPORT.md](JAVA_17_MIGRATION_REPORT.md).
2. Docker daemon is unavailable, preventing container build/start, database connectivity, Flyway, health, proxy, and SPA runtime checks.
3. The configured local PostgreSQL endpoint refused the backend connection during the earlier audit; Docker was unavailable to start the Compose database.
4. Flyway migration execution and database state could not be validated.
5. No frontend automated test command or test suite exists.
6. Backend test coverage is limited to one health-controller test and bypasses security filters.
7. Domain validation, centralized exception handling, database entities, and gameplay APIs are not implemented; setup review cannot establish their future behavior.
8. The game route's Phaser chunk exceeds the Vite 500 kB warning threshold.
9. All backend requests are currently permitted without authentication, as documented for this initial scaffold.

## 9. Fixes Applied

Java 17 migration changed `backend/pom.xml`, both Java base images in `backend/Dockerfile`, and the Java version references in `README.md` and `backend/README.md`. No dependencies, business logic, or database configuration were changed.

## 10. Remaining Risks

- The backend build must run with a JDK 17 installation.
- Database credentials, connectivity, PostgreSQL 16 runtime, Flyway application, and schema validation remain unverified.
- Dockerfile builds, container health checks, Compose networking, and Nginx runtime behavior remain unverified.
- There is no automated frontend test suite and only one narrow backend test.
- Security, request validation, and error response behavior need explicit design and tests before business APIs are introduced.
- The backend-authoritative scoring/reward rule is currently a design constraint only; future API contracts and persistence must ensure clients cannot submit final outcomes as trusted values.

## 11. Readiness Assessment

**Conditionally ready for early feature development as a UI/backend scaffold; backend build is verified on Java 17, but service integration is not verified.**

Frontend typechecking, linting, and production bundling pass. Backend sources and the existing test pass without a version override when Maven runs on Java 17. Database migration, full-stack networking, and container runtime readiness could not be confirmed. Establish a working Docker environment before relying on backend startup or migration behavior.

## 12. Recommended Next Development Phase

1. Configure developer and CI Maven environments to use JDK 17.
2. Start Docker Desktop, then perform the blocked Phase 2.2 PostgreSQL, Flyway, backend, and frontend container checks recorded in [PHASE_2_2_DOCKER_DATABASE_REPORT.md](PHASE_2_2_DOCKER_DATABASE_REPORT.md).
3. Define initial backend domain module boundaries, API contracts, validation rules, and error response conventions before implementing gameplay behavior.
4. Design persistence through additive Flyway migrations and add PostgreSQL-backed integration tests for schema validation and API persistence.
5. Add authentication/authorization before exposing progress, mission, score, or reward operations; keep final reward and score decisions on the backend.
6. Add frontend route/API tests and a basic browser smoke test; monitor or further split the Phaser bundle if the game route's load cost is unacceptable.

## 13. Phase 2.2 Docker and Database Integration

- **Compose configuration:** PASS (`docker compose config --quiet`); resolved services are `database`, `backend`, and `frontend`, with named volume `postgres_data`.
- **Docker Engine:** BLOCKED. Both `desktop-linux` and `default` endpoints failed because their named pipes are missing; Docker Desktop reports that it is not running. No context was changed.
- **Runtime checks:** PostgreSQL startup/health/connectivity, Flyway history and schema, backend health/restarts, frontend health, Nginx proxy, and SPA fallback are NOT VERIFIED because containers could not be started.
- No volumes were deleted, no containers were started, and no credentials were printed or recorded.
