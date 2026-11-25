# PHASE 2.4.2.2 — SESSION EXPIRATION IMPLEMENTATION PLAN

## 1. Approval gate

Approval is required before Step C for:

- V9 schema addition
- `last_activity_at = started_at` backfill
- pessimistic-write session locking
- scheduled batch expiration
- injectable `Clock`

No application code or migration is changed until approval.

## 2. Proposed database change

Create `V9__game_session_expiration.sql`:

```sql
ALTER TABLE app.game_sessions
    ADD COLUMN last_activity_at TIMESTAMP;

UPDATE app.game_sessions
SET last_activity_at = started_at
WHERE last_activity_at IS NULL;

ALTER TABLE app.game_sessions
    ALTER COLUMN last_activity_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_game_sessions_active_expiration
    ON app.game_sessions (last_activity_at, started_at)
    WHERE status = 'ACTIVE';
```

V1–V8 remain immutable.

No active development database will be changed during implementation testing.

## 3. Ordered implementation tasks

### T1 — Migration and entity

Files:

- `backend/src/main/resources/db/migration/V9__game_session_expiration.sql`
- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionResponse.java`

Work:

- add `lastActivityAt`
- initialize it consistently with `startedAt`
- map it with the existing `TIMESTAMP` convention
- expose it in session DTO

Verification:

- H2 JPA persistence
- PostgreSQL V1–V9 migration
- Hibernate `ddl-auto=validate`
- backfill verification

### T2 — Injectable time source

Files:

- `backend/src/main/java/com/vietsuuky/config/TimeConfig.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`

Work:

- provide one `Clock` bean
- inject it into session service and scheduler
- centralize exact boundary calculations

Boundary rule:

```text
eligible when now - lastActivityAt < 30 minutes
and now - startedAt < 120 minutes
```

At exactly one of those durations, session expires.

### T3 — Row-lock repository and centralized transition

Files:

- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`

Work:

- add owner-and-ID pessimistic lookup
- add scheduler batch lookup with pessimistic locking
- centralize `ACTIVE -> EXPIRED`
- set `endedAt`
- reject expired operations
- preserve existing completion, abandonment, and event semantics

Race policy:

- every mutation loads the session under a row lock
- expiration and event/completion/abandonment serialize on the session row
- first terminal transition wins
- stale writes cannot restore ACTIVE
- optimistic lock conflicts are handled explicitly

### T4 — Lazy expiration

Apply T3 behavior to:

- `getSessionForPlayer`
- `recordEvent`
- `completeSession`
- `abandonSession`

Rules:

- overdue retrieval transitions to EXPIRED and returns terminal state
- overdue event rejects and transitions to EXPIRED
- overdue completion transitions to EXPIRED and stops before reward/progress mutations
- overdue abandonment transitions to EXPIRED instead of ABANDONED

### T5 — Scheduled sweep

Files:

- `backend/src/main/java/com/vietsuuky/session/service/GameSessionExpirationScheduler.java`

Work:

- enable scheduling
- fixed-delay batch sweep
- lock ACTIVE overdue rows
- transition in batches
- preserve terminal states

No full-table scan on every request.

### T6 — Unit and integration tests

Primary test file:

- `backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java`

Additional files:

- session expiration unit test
- scheduler integration test
- PostgreSQL opt-in expiration/concurrency test

Coverage:

- both timeout boundaries
- exactly and over each boundary
- earlier expiration condition
- event refresh
- no absolute lifetime extension
- terminal-state immutability
- ended-at set once
- GET/event/complete/abandon behavior
- reward and mission-progress prevention
- scheduled sweep

### T7 — PostgreSQL concurrency verification

Use a fresh disposable PostgreSQL 16 database.

Verify:

- V1–V9
- backfill
- Hibernate validate
- expiration index
- concurrent event versus expiry
- concurrent completion versus expiry
- concurrent abandonment versus expiry
- only one terminal transition wins
- stale writes cannot restore ACTIVE

No active development database or Docker volume will be used.

### T8 — Regression and final report

Run Java 17 backend tests and report exact counts.

Create:

- `PHASE_2_4_2_2_FINAL_REPORT.md`
- `batch-report.yaml`
- `checkpoints/tasks-to-impl.yaml`

## 4. Concurrency fallback

If repository row locking or transaction isolation is unavailable in H2 or the PostgreSQL test environment, stop and report a blocker rather than substituting a Java-only check.

Optimistic `@Version` remains a secondary stale-write guard, not the sole expiration mechanism.

## 5. Out of scope

- completion evidence redesign
- score policy changes
- reward redesign
- save functionality
- React/Phaser
- mission content
- V1–V8 edits
- active database reset
- Docker volume deletion

## 6. Approval question

Step C may begin only after approval of V9 and the pessimistic-lock plus scheduled-sweep design.
