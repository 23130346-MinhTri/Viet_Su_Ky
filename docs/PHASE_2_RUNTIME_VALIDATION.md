# Historical Phase 2.1: Java 21 Alignment and Runtime Validation

> Historical record of Java 21 validation before the Java 17 migration. The current backend target and subsequent verification are documented in [JAVA_17_MIGRATION_REPORT.md](JAVA_17_MIGRATION_REPORT.md).

**Date:** 2026-11-05  
**Project:** VIỆT SỬ KÝ  
**Outcome:** Java 21 alignment and Maven verification completed. Docker-dependent runtime validation is blocked because Docker Engine is not running in this environment.

## Files Changed

| File | Change | Reason |
|---|---|---|
| `backend/pom.xml` | Changed `java.version` from 25 to 21 | Align compilation with the confirmed Java 21 LTS target. |
| `backend/Dockerfile` | Changed the Maven build image and JRE runtime image from Temurin 25 to Temurin 21 | Ensure container compile and runtime JDK versions match the Maven target. |
| `README.md` | Changed the documented backend Java version to 21 | Keep primary setup instructions consistent with the project target. |
| `backend/README.md` | Changed the documented backend target to Java 21 | Keep backend-specific setup instructions consistent. |
| `docs/SETUP_AUDIT_REPORT.md` | Updated Java version, build result, readiness, and limitations | Replace the previous Java 25 mismatch finding with this phase's verified result. |
| `docs/PHASE_2_RUNTIME_VALIDATION.md` | Added this execution report | Record evidence, blocked checks, and follow-up actions. |

No dependency versions, database settings, business logic, or Docker volumes were changed.

## Java and Maven Environment

The shell's ambient `JAVA_HOME` remains set to `C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot`. The `java` and `javac` commands resolve to Java 21.0.10. To ensure Maven used Java 21 without changing the persistent machine environment, the build process explicitly set `JAVA_HOME` and prepended its `bin` directory to `PATH`.

| Tool | Actual version used |
|---|---|
| JDK (`JAVA_HOME`) | Java 21.0.10, `C:\Program Files\Java\jdk-21.0.10` |
| `java -version` | `21.0.10` (2026-01-20 LTS) |
| `javac -version` | `javac 21.0.10` |
| Maven | Apache Maven 3.9.15 |
| Maven runtime | Java 21.0.10, Oracle Corporation |

Before the process-level JDK selection, `mvn -version` showed Java 17.0.16 from the ambient `JAVA_HOME`. The build below is the actual Java 21 run, not a `-Djava.version` override.

## Commands and Results

### Maven test and package

Executed from `backend/`:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
java -version
javac -version
mvn -version
mvn clean test package
```

Result: **PASS**, exit code 0.

Relevant output:

```text
Java version: 21.0.10, vendor: Oracle Corporation
Compiling 4 source files with javac [debug parameters release 21]
Compiling 1 source file with javac [debug parameters release 21]
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
Building jar: backend/target/viet-su-ky-backend-0.1.0-SNAPSHOT.jar
BUILD SUCCESS
Total time: 14.103 s
```

The compiler also emitted annotation-processing and Mockito dynamic-agent warnings. They did not fail compilation or tests and are not addressed in this setup-only phase.

### Docker and Compose

Executed:

```powershell
docker compose config --quiet
docker info
git check-ignore .env
```

Results:

- `docker compose config --quiet`: **PASS**, exit code 0; no configuration error output.
- `docker info`: **FAIL**, exit code 1. The client cannot connect to `npipe:////./pipe/dockerDesktopLinuxEngine`; Windows reports the Docker Desktop Linux engine pipe does not exist.
- `git check-ignore .env`: **PASS**; output `.env`. Local environment settings remain ignored.

Docker was confirmed unavailable before any attempt to start containers. Consequently, `docker compose up -d --build`, container inspection/log checks, and container runtime probes were **not run**. No containers or volumes were modified.

### Configured endpoints

The ignored local `.env` was inspected without printing credential values. It configures:

| Service | Host port | Container/internal target |
|---|---:|---|
| PostgreSQL | 5433 | `database:5432` from the backend container |
| Backend | 8082 | 8080 inside Compose |
| Frontend/Nginx | 8081 | 80 inside Compose |

Compose health paths from the repository configuration are PostgreSQL `pg_isready`, backend `/actuator/health`, and frontend `/`. The backend API is `/api/v1/health`, and Nginx forwards `/api/` to `backend:8080`.

## Integration Validation Status

| Check | Result | Evidence |
|---|---|---|
| Compose syntax/interpolation | **PASS** | `docker compose config --quiet`, exit 0. |
| Docker daemon | **FAIL / BLOCKER** | `docker info` cannot open the Docker Desktop Linux engine named pipe. |
| Container creation and health | **BLOCKED / NOT RUN** | Docker daemon unavailable. |
| PostgreSQL 16 connectivity from backend | **BLOCKED / NOT RUN** | Requires Compose database and backend containers. |
| Flyway migration execution | **BLOCKED / NOT RUN** | Application could not be started against the Compose database. |
| Flyway schema history | **BLOCKED / NOT RUN** | Cannot query PostgreSQL without the database container. |
| Backend health endpoint | **BLOCKED / NOT RUN in this phase** | Expected published URL is `http://localhost:8082/actuator/health`; container could not start. |
| Backend API health | **BLOCKED / NOT RUN in this phase** | Expected URL is `http://localhost:8082/api/v1/health`; container could not start. |
| Frontend health | **BLOCKED / NOT RUN** | Expected URL is `http://localhost:8081/`; Nginx container could not start. |
| Frontend-to-backend request | **BLOCKED / NOT RUN** | Browser/UI and API proxy services were unavailable. |
| Nginx `/api/` proxy | **BLOCKED / NOT RUN** | Static config maps `/api/` to `http://backend:8080`; no live proxy request was possible. |
| Nginx SPA fallback | **BLOCKED / NOT RUN** | Static config uses `try_files $uri $uri/ /index.html`; no live deep-route request was possible. |
| Restart-loop and service logs check | **BLOCKED / NOT RUN** | No Compose containers could be created or inspected. |

During the prior setup audit, a separate local backend startup attempt failed when Flyway attempted to connect to `localhost:5433`; the connection was refused. That result is evidence of an unavailable local database endpoint, not a Flyway migration failure. The present phase did not retry that startup because the required Docker engine was unavailable.

## Passed and Failed Checks

**Passed**

- Java 21 target is consistent across the Maven property and both backend Docker stages.
- Maven ran on JDK 21.0.10 and `mvn clean test package` succeeded without a version override.
- The backend test result is 1 passed, 0 failed, 0 errors, 0 skipped.
- `docker compose config --quiet` passed.
- `.env` is ignored by Git.

**Failed or blocked**

- Docker Engine availability check failed because the Docker Desktop Linux engine pipe is absent.
- PostgreSQL/Flyway, container health, backend/frontend runtime, Nginx proxy, and SPA fallback checks are blocked and remain unverified.
- Ambient `JAVA_HOME` still points to Java 17. This phase selected JDK 21 for the Maven process but did not change machine-wide environment settings.

## Remaining Risks

- Developers and CI can accidentally run Maven under Java 17 unless their environment selects JDK 21.
- Flyway V1 (`CREATE SCHEMA IF NOT EXISTS app`) has not been applied or checked against a live database in this phase.
- The local PostgreSQL port and Compose publication differ by design (`5433:5432`); local application startup must use host port 5433, while Compose backend uses `database:5432`.
- Compose service health, startup ordering, network connectivity, Nginx proxying, and browser route fallback remain static-only checks.
- Existing backend coverage consists of one health-controller test. No database integration test is configured.

## Readiness Assessment

**Build-ready on Java 21; runtime integration readiness is undetermined.** The application and test sources compile, the single backend test passes, and the packaged JAR is produced. The project is not yet verified end-to-end because Docker Engine was not available. Do not treat PostgreSQL, Flyway, Nginx, or frontend/backend runtime behavior as passed until the Docker-based checks are rerun.

## Recommended First Business Module

Start with an **Identity / Player Profile** module after the runtime stack is available. Define player identity, ownership, and authorization boundaries before introducing persistent progress or score/reward endpoints. Keep score, mission completion, unlock, and reward decisions authoritative on the backend. No business module was implemented in this phase.
