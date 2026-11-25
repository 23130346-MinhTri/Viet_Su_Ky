# PHASE 2.4.2 — IMPLEMENTATION PLAN

## 1. Approval gate

Before implementation, approve V8 and the scope below.

The required change is:

- Add a PostgreSQL partial unique index to enforce one `ACTIVE` game session per `(player_id, mission_id)`.
- Keep V1–V7 unchanged.
- Do not modify existing data in V8.
- Do not implement mission completion, score trust changes, rewards, saves, frontend, or Phaser changes.

No code should be modified until approval.

## 2. Task sequence

### T1 — Mission query DTO and availability consistency

Files:

- `MissionResponse.java`
- `MissionController.java`
- `MissionService.java`
- mission integration tests

Work:

- Preserve existing mission response envelope.
- Include `available` and `progressStatus` in player-aware mission responses.
- Keep mission detail player-aware through authenticated principal.
- Keep mission GET operations read-only.
- Return `MISSION_NOT_FOUND` or consistent existing error convention for missing missions.
- Filter inactive missions from list while retaining existing API conventions.

Tests:

- Mission list by active status
- Mission detail found/not found
- Missing progress remains `AVAILABLE`, not completed
- Inactive mission unavailable
- Prerequisite completed/locked behavior
- Mission GET does not create mission-progress rows

### T2 — Add V8 active-session uniqueness

File:

- `V8__active_session_uniqueness.sql`

Work:

- Add a unique partial index on `(player_id, mission_id)` where `status = 'ACTIVE'`.
- Preserve terminal sessions and allow multiple active sessions for different missions.
- Perform no data rewrite or cleanup.

Tests:

- Flyway V1–V8 on a fresh disposable PostgreSQL 16 database
- Hibernate `ddl-auto=validate`
- Direct duplicate ACTIVE insert rejected
- COMPLETED/ABANDONED/EXPIRED duplicates allowed
- Multiple missions allowed for one player

### T3 — Session creation transaction

Files:

- `GameSessionRepository.java`
- `GameSessionService.java`
- `GameSessionController.java`
- maybe common exception classes

Work:

- Derive player exclusively from authenticated principal.
- Validate mission existence and active availability.
- Reject locked/inactive missions.
- Lazy-create/update mission progress inside the transaction.
- Set progress `IN_PROGRESS` when starting.
- Create `ACTIVE` session.
- Add application duplicate detection.
- Translate database unique-index violations into a conflict response.
- Return HTTP `201 Created`.
- Enforce transactional rollback on failed creation.

Tests:

- Successful session creation
- Missing mission
- Locked mission
- Lazy progress creation
- Existing progress marked `IN_PROGRESS`
- Duplicate active session
- Concurrent start requests
- Transaction rollback
- Multiple active sessions for different missions

### T4 — Session retrieval and ownership

Files:

- `GameSessionController.java`
- `GameSessionService.java`
- session tests

Work:

- Keep owner lookup by `(sessionId, playerId)`.
- Use authenticated principal as owner.
- Return `SESSION_NOT_FOUND`/consistent existing access-denied behavior for cross-player access.

Tests:

- Owner retrieval
- Cross-player retrieval rejection
- Missing session

### T5 — Regression and PostgreSQL verification

Existing verification:

- Maven H2 tests
- Phase 2.3 and 2.4.1 regression tests
- Java 17
- Fresh disposable PostgreSQL 16
- Spring startup and actuator health

The existing completion/event/abandon endpoints must remain out of scope and are not to receive new behavior or score/reward changes.

## 3. Database change

V8 is required. Existing V1–V7 remain immutable.

Proposed SQL:

```sql
CREATE UNIQUE INDEX uk_game_sessions_one_active_per_player_mission
    ON app.game_sessions (player_id, mission_id)
    WHERE status = 'ACTIVE';
```

Before creating V8, verify that no duplicate ACTIVE session already exists in the target database. If duplicates exist, migration must stop or require a separately approved data remediation step.

## 4. API outcomes

Phase 2.4.2 endpoints:

- `GET /api/v1/missions`
- `GET /api/v1/missions/{missionId}`
- `POST /api/v1/game-sessions`
- `GET /api/v1/game-sessions/{sessionId}`

Existing `/availability` may remain as an existing approved contract endpoint.

Expected status behavior:

- Mission/session success as existing response envelope
- `201` for session creation
- `400/404/409` consistently mapped according to repository convention
- `401/403` from authentication/authorization

## 5. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Duplicate ACTIVE insert under concurrency | PostgreSQL partial unique index |
| Existing duplicate ACTIVE rows block migration | Preflight duplicate check before approval/implementation |
| Unique constraint violation not translated | Catch the persistence/data-integrity exception in service/controller boundary |
| Mission availability drift | Centralize status resolution in `MissionService` |
| Lazy progress race | Database unique `(player_id, mission_id)` plus transaction handling |
| Transaction rollback gaps | Test failed mission/constraint scenarios |
| Scope expansion | Exclude completion evidence, rewards, saves, UI, and Phaser |

## 6. Stop condition

The audit found a required schema change, so implementation is blocked until the migration design is approved by the review owner.
