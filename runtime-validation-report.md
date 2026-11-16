# Runtime Validation Report

**Generated**: 2026-11-08T23:48:00+07:00  
**Target**: `D:\Games\History`

## Summary

| Step | Status | Exit Code | Details |
|---|---|---:|---|
| Compose startup | PASS | 0 | Database, backend, and frontend containers healthy. |
| PostgreSQL migration | PASS | 0 | Flyway V1 and V2 both recorded as successful. |
| Nginx/API smoke | PASS | 0 | Frontend HTTP 200; proxied API health HTTP 200; invalid registration HTTP 400 as expected. |
| Backend integration | PASS | 0 | 7 tests, 0 failures, 0 errors, 0 skipped. |
| Browser E2E | PASS | 0 | 3 Chromium flows passed against the Vite-hosted frontend. |
| Frontend lint | PASS | 0 | No errors; one Fast Refresh warning remains. |
| Frontend build | PASS | 0 | TypeScript check and Vite production build succeeded. |

**Overall**: PASS.

## Environment

- Java 21.0.10; Maven 3.9.15.
- Docker: AVAILABLE — Compose rebuilt and launched the project stack.
- Node.js: AVAILABLE — v24.14.1.
- Playwright: AVAILABLE — v1.65.0; Chromium installation succeeded.
- Database: PostgreSQL 16; existing Compose database and volume preserved.
- Active services: frontend `http://localhost:8081`, backend `http://localhost:8082`, PostgreSQL host port `5433`.
- Flyway history: V1 `create app schema` and V2 `create account and player profile`, both successful.

## Test Evidence

- `backend`: `mvn test -q` — exit code 0; 7 tests passed.
- `frontend`: `npm run test:e2e -- --reporter=list` — exit code 0; 3 tests passed:
  - Login/registration navigation.
  - Unauthenticated profile redirect.
  - Registration form validation.
- `frontend`: `npm run lint` — exit code 0; one `react-refresh/only-export-components` warning.
- `frontend`: `npm run build` — exit code 0; TypeScript check and production bundle succeeded.
- `AuthPlayerHttpIntegrationTest` starts Spring Boot with `RANDOM_PORT` and verifies real HTTP registration, login, protected profile read/update/read-back, and unauthenticated access rejection.
- Deployed stack smoke checks: frontend returned HTTP 200, backend Actuator returned `{"status":"UP"}`, Nginx `/api/v1/health` returned HTTP 200, and invalid registration/login payloads returned expected HTTP 400 responses.
- Surefire XML reports are in `backend/target/surefire-reports/`.

## Issues Found and Resolved

- Profile API returned HTTP 500 because a lazy `PlayerProfile.account` association was accessed after its persistence context closed. The API now fetches the account and maps the DTO within the service transaction; integration coverage verifies the fix.
- Playwright navigation locator matched two identical “Đăng nhập” links. The test now scopes the link to the main content.
- React lint flagged synchronous state updates in effects. Auth loading and profile drafts now avoid those updates; successful registration routes to login because registration does not issue a JWT.
- The existing Nginx API proxy removed the `/api` path segment and returned 404. The proxy now preserves the full path.
- The API health endpoint was denied by Spring Security. It is now explicitly public.

## Known Gaps

- Browser E2E covers frontend routing and form behavior. The authenticated register/login/profile sequence is verified over real HTTP in the backend integration test, not through a browser connected to the persistent Compose stack.
- `npm install` reported 10 moderate dependency advisories; they were not assessed or remediated in this validation task.
- Vite reported the existing large game chunk warning during production build.

## Verdict

```text
environment:
  docker: AVAILABLE — Docker Compose rebuilt and reports database/backend/frontend healthy
  node: AVAILABLE — v24.14.1
  playwright: AVAILABLE — v1.65.0; Chromium installed
  infra-tier: PRIMARY(Docker-based) — PostgreSQL 16; Flyway V1 and V2 successful
  browser-tier: PRIMARY(Playwright) — 3 Chromium tests passed
startup: PASS — Docker Compose; database, backend, frontend healthy; direct actuator HTTP 200
integration: PASS — exit_code: 0, passed: 7, failed: 0, skipped: 0; auth/profile HTTP flows and PostgreSQL migration applied
e2e: PASS — exit_code: 0, passed: 3, failed: 0; auth navigation, protected route, registration validation; no browser-to-backend authenticated flow
overall: PASS — Compose stack is running; integration, browser tests, lint, and build passed
```
