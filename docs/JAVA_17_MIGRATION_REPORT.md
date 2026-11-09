# Java 17 Migration Report

**Date:** 2026-11-07  
**Project:** VIỆT SỬ KÝ  
**Result:** Backend targets Java 17. Maven clean test and package passed on JDK 17. Docker Compose configuration passed validation, but container build/runtime checks are blocked because Docker Engine is unavailable.

## Files Changed

| File | Change |
|---|---|
| `backend/pom.xml` | Set the Spring Boot-managed Java compiler target to 17. |
| `backend/Dockerfile` | Use Temurin 17 for both the Maven build image and JRE runtime image. |
| `README.md` | Document Java 17 as the backend runtime. |
| `backend/README.md` | Document Java 17 as the backend target. |
| `docs/SETUP_AUDIT_REPORT.md` | Update the current Java target and build/runtime status. |
| `docs/PHASE_2_RUNTIME_VALIDATION.md` | Mark the Java 21 report as historical and link to this report. |
| `docs/JAVA_17_MIGRATION_REPORT.md` | Record this migration and its verification evidence. |

No GitHub Actions workflow files were present. No database, business logic, architecture, or dependency version was changed.

## Compatibility Review

- Spring Boot 3.5.3 supports Java 17; the existing dependency versions were retained.
- The source uses records and `Stream.toList()`, both available before Java 17. No Java 21-only syntax or standard-library API was found in the backend source.
- The existing Maven build plugins resolved successfully and compiled both main and test code with `release 17`.
- Maven dependency-tree resolution completed successfully on JDK 17.

## Maven Verification

Executed from `backend/` with `JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot` and its `bin` directory first on `PATH`:

```text
java -version: 17.0.16 LTS
javac -version: javac 17.0.16
mvn -version: Apache Maven 3.9.15, Java 17.0.16
mvn clean test package: BUILD SUCCESS
Compilation target: release 17 (main and test)
Tests: 1 run, 0 failures, 0 errors, 0 skipped
```

The test log also confirmed Spring Boot tests ran using Java 17. The Maven verification did not use a `-Djava.version` override.

## Docker Verification

- `docker compose config --quiet`: **PASS**.
- Docker Engine availability: **BLOCKED**; the Docker Desktop Linux engine named pipe was missing.
- `docker compose up -d --build`, container inspection, and logs: **NOT VERIFIED** because the engine is unavailable. No containers or volumes were modified.

## Remaining Issues

- Container image build and runtime behavior are not verified until Docker Engine is available.
- PostgreSQL connectivity and Flyway runtime behavior remain unverified, consistent with the earlier setup audit.
- No Java compatibility or Maven test failures remain from this migration.
