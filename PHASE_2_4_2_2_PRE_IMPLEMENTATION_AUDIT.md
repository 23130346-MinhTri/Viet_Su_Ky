# PHASE 2.4.2.2 — SESSION EXPIRATION PRE-IMPLEMENTATION AUDIT

## 1. Audit scope

Repository: `23130346-MinhTri/Viet_Su_Ky`

Working directory: `D:\Games\History`

This audit inspects the current source before any implementation. V1–V8, active database data, Docker volumes, and application behavior were not modified.

## 2. Approved policy

The implementation must use:

- inactivity timeout: 30 minutes without a validated session event
- absolute lifetime: 120 minutes from `started_at`
- exact inactivity boundary: exactly 30 minutes expires
- exact lifetime boundary: exactly 120 minutes expires
- eligible only when elapsed inactivity and elapsed lifetime are both strictly less than their limits
- overdue `ACTIVE` sessions transition to `EXPIRED`
- expiration sets `ended_at`
- terminal states remain terminal
- expired sessions cannot record events, complete, abandon successfully, or issue rewards

The earlier of inactivity or absolute lifetime expiration takes precedence.

## 3. Current implementation

### 3.1 Entity

`GameSession` currently has:

- `startedAt`
- `endedAt`
- `status`
- `eventCount`
- `finalScore`
- `metadata`
- `@Version version`
- `createdAt`
- `updatedAt`

It does not have `lastActivityAt`.

Evidence:

- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/resources/db/migration/V3__game_session_and_progress.sql`

V3 persists `started_at`, `ended_at`, `status`, `event_count`, `final_score`, `metadata`, `created_at`, and `updated_at`. V8 only adds the partial unique index for one ACTIVE session per player and mission.

### 3.2 Service

`GameSessionService` has:

- `createSession`
- `getSessionForPlayer`
- `recordEvent`
- `completeSession`
- `abandonSession`
- `toResponse`

None evaluates expiration. `recordEvent` and `completeSession` use only `status == ACTIVE`.

`recordEvent` updates:

- `eventCount`
- session `metadata`
- `MissionProgress.lastEventAt`

`MissionProgress.lastEventAt` is not session activity and is not used to enforce timeout.

Evidence:

- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`

### 3.3 Repository

`GameSessionRepository` provides:

- list by player
- find by ID and player
- existence check for ACTIVE player/mission

It has no:

- pessimistic owner/session lookup
- expiration query
- last-activity field
- scheduled batch query

Evidence:

- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`

### 3.4 Controller and DTO

The controller passes the authenticated player ID to service methods. Session create and get endpoints expose `startedAt` and `endedAt`.

No endpoint performs expiration or exposes a last-activity timestamp.

Evidence:

- `backend/src/main/java/com/vietsuuky/session/controller/GameSessionController.java`
- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionResponse.java`

### 3.5 Scheduled jobs

Repository inspection found no:

- `@Scheduled`
- `@EnableScheduling`
- scheduler configuration
- expiration sweep

There is no distributed scheduler dependency.

### 3.6 Tests

Existing session tests cover:

- session creation and completion
- cross-player access
- duplicate ACTIVE sessions
- locked mission rejection
- mission reads without progress writes

No tests cover expiration boundaries, expired event/completion/abandon behavior, scheduled sweep, terminal immutability, or expiration concurrency.

Evidence:

- `backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java`
- `backend/src/test/java/com/vietsuuky/Phase24FoundationIntegrationTest.java`
- `backend/src/test/java/com/vietsuuky/Phase2416PostgresIntegrationTest.java`

## 4. Database requirements

A forward-only V9 migration is required.

Recommended V9:

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

This matches the existing PostgreSQL `TIMESTAMP` convention. V1–V8 remain unchanged.

The migration backfills existing rows from `started_at`. New sessions will set `startedAt` and `lastActivityAt` to the same injected `Clock` instant.

## 5. Locking and race strategy

The current `@Version` field is retained.

Recommended policy:

1. Use a repository pessimistic-write lookup for mutations and owner retrieval.
2. The lookup must lock the session row and include owner and session ID.
3. Run expiration checks and mutations in `@Transactional` methods.
4. If the session is overdue:
   - set status `EXPIRED`
   - set `endedAt` to the single injected `Clock` instant
   - save/flush inside the transaction
   - reject the operation
5. If the session is active and eligible:
   - event refreshes `lastActivityAt`
   - completion transitions to `COMPLETED`
   - abandonment transitions to `ABANDONED`
6. A stale or concurrent operation waiting behind the row lock re-reads the locked row state.
7. Optimistic-lock conflicts remain mapped explicitly if they escape the locking path.
8. No operation continues to reward or mission-progress mutation after expiration is detected.

Pessimistic locking is preferred for this lifecycle because expiration, event, completion, abandonment, retrieval, and scheduler updates all operate on the same session row.

The V8 partial unique index remains responsible for duplicate ACTIVE session start integrity.

## 6. Scheduler design

No distributed dependency is needed.

Recommended single modular-monolith scheduler:

- enable Spring scheduling
- run a fixed-delay job, for example once per minute
- process ACTIVE sessions in batches
- use one transaction per batch or one row at a time
- lock each batch row with `PESSIMISTIC_WRITE`
- calculate inactivity and lifetime using the injected `Clock`
- transition only ACTIVE overdue rows to `EXPIRED`
- set `endedAt`
- stop or continue a batch without modifying terminal sessions

Lazy expiration remains mandatory in:

- GET retrieval
- event recording
- completion
- abandonment
- scheduled sweep only supplements lazy checks

## 7. Exact files to modify

### Migration

- `backend/src/main/resources/db/migration/V9__game_session_expiration.sql`

### Domain

- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`

### Repository

- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`

### Service

- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`

### Scheduling

- `backend/src/main/java/com/vietsuuky/session/service/GameSessionExpirationScheduler.java`
- `backend/src/main/java/com/vietsuuky/config/TimeConfig.java`

One or both config and scheduler locations may follow an existing project package convention after implementation inspection.

### DTO

- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionResponse.java`

The response should expose `lastActivityAt` because session lifecycle state is persistence-visible and the DTO currently exposes session timestamps. Internal persistence fields not relevant to the API remain excluded.

### Error handling

- `backend/src/main/java/com/vietsuuky/common/exception/ApiException.java`
- `backend/src/main/java/com/vietsuuky/common/exception/GlobalExceptionHandler.java`

Only if stable `SESSION_EXPIRED` and optimistic-conflict mapping are absent.

### Tests

- `backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java`
- a focused expiration unit test class
- a focused expiration scheduler test class
- PostgreSQL opt-in integration coverage

Existing test resources may require PostgreSQL partial-index/H2 compatibility checks.

## 8. Missing functionality

- `last_activity_at`
- injectable `Clock`
- expiration policy implementation
- lazy expiration on retrieval
- lazy expiration before events
- lazy expiration before completion
- lazy expiration before abandonment
- scheduled sweep
- row-lock repository methods
- boundary tests
- concurrent PostgreSQL tests
- explicit expired-session error mapping

## 9. Security and concurrency risks

- `recordEvent`, `complete`, and `abandon` currently use a plain repository lookup; they can act on stale entity state.
- Public setters permit state changes outside the service, but production API paths should not expose entities.
- Completion is currently outside the audited expiration scope and may issue rewards, so expiration must run before reward code.
- `MissionProgress.lastEventAt` cannot be used as the source of session inactivity.
- A scheduled sweep without lazy checks is insufficient.
- Lazy checks without row locks are insufficient for concurrency.
- V9 is safe only if no existing duplicate `lastActivityAt` or null data prevents backfill; the proposed backfill is unconditional and should preserve `started_at`.

## 10. Audit result

**BLOCKED pending implementation approval**

No stop condition is triggered at the audit level:

- V1–V8 do not need editing.
- V9 can be forward-only.
- Pessimistic locking plus `@Version` provides a viable race design.
- Expiration can be checked before existing completion/reward code.
- The implementation scope remains within Phase 2.4.2.2.

The audit stops before code modification.
