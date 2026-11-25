# PHASE 2.4.2 — MISSION & GAME SESSION FOUNDATION

## 1. Role

Act as a senior backend engineer responsible for implementing the next approved phase of the VIỆT SỬ KÝ project.

Repository: `23130346-MinhTri/Viet_Su_Ky`

Working directory: `D:\Games\History`

You must follow the existing project architecture, coding conventions, security requirements, and documentation.

## 2. Mandatory pre-implementation reading

Before modifying code, inspect:

- `docs/README.md`
- `AI_WORKFLOW.md`
- `PROJECT_CONTEXT.md`
- `PRODUCT_GDD.md`
- `TECHNICAL_DESIGN.md`
- `SRS.md`
- `ARCHITECTURE.md`

Also inspect the actual repository, especially:

- Existing Phase 2.3 account and authentication implementation.
- Existing Phase 2.4 entities, repositories, services, controllers, DTOs, and tests.
- Flyway migrations V1–V7.
- Existing security configuration and API response conventions.
- `PHASE_2_4_1_6_FINAL_REMEDIATION_REPORT.md`.

Do not assume the previous implementation reports are equivalent to the current repository state.

## 3. Phase status and baseline

Phase 2.4.1 is CLOSED with PASS WITH WARNINGS.

Verified baseline reported:

- Java 17.
- Spring Boot 3.5.3.
- PostgreSQL 16.
- Flyway V1–V7.
- Hibernate `ddl-auto=validate`.
- Existing authentication and player profile functionality.
- PostgreSQL-specific tests: 4 passed.
- Regression tests: 14 passed.

Important schema decisions:

- `GameSession.version` uses Java `Integer`, PostgreSQL `INTEGER`, and `@Version`.
- `GameSave.version` uses Java `Integer` and `@Version`.
- `PlayerReward` does not use `@Version`.
- V1–V7 are immutable.
- New schema changes require forward-only migrations V8+.

These are baseline facts to verify against the actual repository, not assumptions to blindly reproduce.

## 4. Objective

Implement the Mission & Game Session Foundation.

The goal is to establish the backend foundation for:

1. Reading available missions.
2. Retrieving mission details.
3. Starting a game session.
4. Retrieving the current player's session.
5. Persisting valid session state transitions.

Do not implement mission completion, scoring, reward issuance, or save/load workflows in this phase.

## 5. Scope

### 5.1 Mission query

Implement or complete:

- `GET /api/v1/missions`
- `GET /api/v1/missions/{missionId}`

Requirements:

- Return missions from the database.
- Respect the existing authentication and authorization model.
- Do not trust player identity from request parameters or request bodies.
- Use the authenticated principal to determine the current player.
- Apply existing mission availability/prerequisite rules if already defined and approved in the project design.
- Do not invent new mission-unlocking rules.
- Do not write progress records during GET requests.
- A missing `mission_progress` record must not be interpreted as mission completion.
- Locked missions must be represented consistently with existing DTO/API conventions.
- Return appropriate HTTP status codes for missing or inaccessible resources.

### 5.2 Start game session

Implement or complete:

- `POST /api/v1/game-sessions`

Requirements:

- Require authentication.
- Derive `playerId` exclusively from the authenticated principal.
- Validate that the requested mission exists.
- Validate mission availability using the existing approved domain rules.
- Reject attempts to start locked or unavailable missions.
- Ensure mission progress exists using the approved lazy-initialization behavior.
- Set mission progress to `IN_PROGRESS` when appropriate.
- Create a new `ACTIVE` session.
- Enforce one active session per player per mission.
- Allow multiple active sessions for different missions if the approved design permits it.
- Persist all related changes transactionally.
- Prevent unauthorized access to another player's data.

Do not silently create duplicate active sessions.

If the repository's current schema or design does not support a requirement, stop and report the gap instead of inventing a workaround.

### 5.3 Session query

Implement or complete:

- `GET /api/v1/game-sessions/{sessionId}`

Requirements:

- Only the owning player can access the session.
- Another player must not be able to inspect or manipulate it.
- Return session state using the existing response envelope.
- Do not expose internal persistence fields unnecessarily.

### 5.4 Session state management

Preserve the approved state machine:

- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

For this phase:

- New sessions start as `ACTIVE`.
- Only implement state transitions explicitly supported by existing approved requirements.
- Do not add arbitrary transitions.
- Terminal states must not return to `ACTIVE`.
- Respect existing inactivity and absolute session lifetime policies if already implemented.
- Do not implement mission completion or reward issuance.

If an endpoint for abandoning a session is required by the existing specification, implement it only if its transition rules and authorization are already defined. Otherwise report the gap.

## 6. Architecture and security constraints

- Keep the modular monolith architecture.
- Keep controller, service, repository, entity, and DTO responsibilities separate.
- Controllers must not contain business logic.
- Services must own transactional business operations.
- Use Spring Security authentication principal.
- Do not accept trusted player identity from client input.
- Do not expose entity objects directly through APIs.
- Preserve consistent validation and error responses.
- Use database constraints to protect important invariants where supported.
- Do not weaken existing security configuration.
- Do not introduce unnecessary dependencies.
- Avoid unrelated refactoring.

## 7. Idempotency and concurrency

Before implementing session-start concurrency behavior:

1. Inspect the existing database constraints and indexes.
2. Inspect the existing `GameSession` repository and service.
3. Determine whether the current schema can safely enforce one active session per player per mission.
4. Determine how concurrent start requests are handled.

Do not claim concurrency safety based only on a Java pre-check.

If the database cannot enforce the invariant safely, propose a forward-only migration and explain the concurrency design before implementing it.

Do not implement completion idempotency or reward idempotency in this phase.

## 8. Testing requirements

Add tests appropriate to the actual implementation.

### Unit tests

Cover:

- Mission availability decisions.
- Locked mission rejection.
- Session creation rules.
- Active-session uniqueness behavior.
- Valid and invalid state transitions.
- Ownership checks.

### Integration tests

Cover:

- Mission list and detail APIs.
- Authenticated session creation.
- Missing mission.
- Locked mission.
- Mission progress lazy initialization.
- No writes from mission GET endpoints.
- One active session per player per mission.
- Multiple active sessions across different missions where permitted.
- Session retrieval by owner.
- Cross-player access rejection.
- Transaction rollback on failed session creation.

### Regression tests

Run the existing Phase 2.3 and Phase 2.4.1 regression tests.

Use Java 17.

### PostgreSQL verification

Use a fresh disposable PostgreSQL 16 database where needed.

Verify:

- Existing V1–V7 migrations.
- Hibernate `ddl-auto=validate`.
- New migration if one is introduced.
- Database uniqueness and concurrency behavior.
- Application startup and health endpoint.

Do not modify the active development database for isolated verification.

## 9. Strictly out of scope

Do not implement:

- Mission completion endpoint.
- Score calculation.
- Client event evidence validation for completion.
- Reward issuance or reward claiming.
- Player reward balance updates.
- GameSave create/update/load APIs.
- Phaser integration.
- React UI changes.
- New mission content.
- New historical content.
- Analytics or leaderboard.
- Payment, monetization, or advertising.
- Phase 2.4.3 or later functionality.

Do not modify V1–V7.

Do not reset, truncate, or delete existing development data.

Do not delete Docker volumes.

Do not change Java version or Spring Boot version.

Do not change the approved product scope.

## 10. Required workflow

Work in small steps.

### Step A — Repository audit

Inspect the current implementation and produce:

`PHASE_2_4_2_PRE_IMPLEMENTATION_AUDIT.md`

Include:

- Existing files and implementation status.
- Existing API endpoints.
- Existing entities and schema.
- Mission availability rules.
- Session state machine.
- Transaction boundaries.
- Security and ownership behavior.
- Concurrency risks.
- Missing functionality.
- Exact proposed files to modify or create.

Do not modify application code in Step A.

### Step B — Implementation plan

Provide a small implementation plan with:

- Ordered tasks.
- Files affected.
- Database changes, if necessary.
- Tests for each task.
- Risks and mitigations.

Stop and wait for approval before implementing if the audit identifies a required schema change or any conflict with approved design.

### Step C — Implementation

Implement only the approved scope.

Do not bundle later phases.

### Step D — Verification

Run all required tests and PostgreSQL verification.

Record actual commands, Java version, test counts, failures, and environment details.

Do not report tests as passed unless they actually ran successfully.

### Step E — Final report

Create:

`PHASE_2_4_2_FINAL_REPORT.md`

Include:

1. Scope implemented.
2. Files created and modified.
3. API endpoints.
4. Security and ownership verification.
5. Mission availability behavior.
6. Session state transition behavior.
7. Transaction and concurrency design.
8. Database migration details, if any.
9. Unit test results.
10. Integration test results.
11. PostgreSQL verification.
12. Phase 2.3 regression results.
13. Known limitations and warnings.
14. Final status: PASS / PASS WITH WARNINGS / BLOCKED.

## 11. Stop conditions

Stop immediately and report BLOCKED if:

- Existing project documentation conflicts with this prompt.
- Existing schema cannot support the approved requirements safely.
- A migration would require modifying V1–V7.
- Ownership or authorization cannot be enforced.
- Session concurrency cannot be guaranteed safely.
- Existing tests reveal unrelated regressions that cannot be isolated.
- Implementation would require expanding the approved product scope.

Do not proceed to Phase 2.4.3.

## 12. Final instruction

The priority is correctness, security, data integrity, and maintainability — not implementation speed.

Do not assume that passing H2 tests proves PostgreSQL correctness.

Do not invent business rules.

Do not claim completion without evidence.

Complete Step A first and report the findings before proceeding.
