# PHASE 2.4.2.1 — SESSION EXPIRATION POLICY COMPLIANCE AUDIT

## Audit scope

Repository: `23130346-MinhTri/Viet_Su_Ky`

Working directory: `D:\Games\History`

This audit is read-only. No Java source, SQL migration, active database, Docker volume, or implementation behavior was changed.

## 1. Approved policy

The approved Phase 2.4 lifecycle design defines:

- Inactivity timeout: 30 minutes without a validated event.
- Absolute lifetime ceiling: 120 minutes from `started_at`.
- An overdue `ACTIVE` session transitions to `EXPIRED`.
- Expiration sets `ended_at`.
- Terminal states cannot return to `ACTIVE`.
- An expired session is not eligible for reward issuance.

Evidence:

- [PHASE_2_4_GAMEPLAY_LIFECYCLE.md](PHASE_2_4_GAMEPLAY_LIFECYCLE.md): state machine and stale-session policy
- [PHASE_2_4_FINAL_DESIGN_REVIEW.md](PHASE_2_4_FINAL_DESIGN_REVIEW.md): accepted `ACTIVE`, `COMPLETED`, `ABANDONED`, `EXPIRED` lifecycle
- [docs/PHASE 2.4.2 — MISSION & GAME SESSION FOUNDATION.md](docs/PHASE%202.4.2%20%E2%80%94%20MISSION%20&amp;%20GAME%20SESSION%20FOUNDATION.md): session states and terminal-state requirement

The lifecycle document calls 30/120 minutes the "Recommended MVP policy," but the Phase 2.4.2.1 task identifies it as previously approved and this audit treats those values as authoritative.

## 2. Current implementation

### 2.1 30-minute inactivity timeout

**Status: NOT IMPLEMENTED**

No service, repository, entity field, migration, controller, or scheduled job evaluates inactivity.

`GameSession` persists:

- `startedAt`
- `endedAt`
- `status`
- `updatedAt`

It does not persist a dedicated last-activity timestamp.

Evidence:

- [GameSession.java](backend/src/main/java/com/vietsuuky/session/entity/GameSession.java): `startedAt` at lines 47-48, `endedAt` at lines 50-51, `updatedAt` at lines 74-75
- [V3 migration](backend/src/main/resources/db/migration/V3__game_session_and_progress.sql): creates only `started_at`, `ended_at`, `status`, and `updated_at` for sessions
- [V8 migration](backend/src/main/resources/db/migration/V8__active_session_uniqueness.sql): only adds the ACTIVE-session partial unique index

V1–V8 do not add a session inactivity column or expiration index.

### 2.2 120-minute absolute lifetime

**Status: NOT IMPLEMENTED**

The entity stores `startedAt`, but no code compares `startedAt + 120 minutes` against the current time.

Evidence:

- [GameSessionService.java](backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java): `createSession`, `getSessionForPlayer`, `recordEvent`, `completeSession`, and `abandonSession` contain no lifetime calculation
- [GameSessionRepository.java](backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java): contains no expiration query

### 2.3 Persisted timestamps

The session entity and V3 schema persist:

| Timestamp | Purpose | Session-specific expiration use |
|---|---|---|
| `started_at` | Session start time | Available, but never used for lifetime enforcement |
| `ended_at` | Terminal session end time | Set for completion and abandonment, never for EXPIRED transition |
| `created_at` | Insert timestamp | No expiration use |
| `updated_at` | Hibernate update timestamp | Generic entity update timestamp; not used by expiration logic |

The session is also represented in `GameSessionResponse` with `startedAt` and `endedAt`, but no inactivity timestamp is exposed.

### 2.4 What updates activity

The only session activity write is `recordEvent`, which:

1. Loads the session.
2. Rejects a session unless its status is `ACTIVE`.
3. Increments `event_count`.
4. Updates session metadata.
5. Updates `MissionProgress.lastEventAt`.

Evidence:

- [GameSessionService.java](backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java): `recordEvent` updates event count and metadata
- [MissionProgressService.java](backend/src/main/java/com/vietsuuky/progress/service/MissionProgressService.java): `ensureForPlayer` and `mark...` persist `lastEventAt`

However, `MissionProgress.lastEventAt` belongs to mission progress, not the game session, and no expiration logic reads it.

There is no:

- Dedicated `last_activity_at` on `GameSession`
- Expiration-aware event update
- 30-minute or 120-minute check

`@UpdateTimestamp` may update `GameSession.updatedAt` when the entity is saved, but it is generic persistence metadata and is not defined or queried as a session activity timestamp.

### 2.5 Expired-session detection

**Status: NOT IMPLEMENTED**

No scheduled job or lazy detection mechanism exists.

Repository-wide inspection found no `@Scheduled`, `EnableScheduling`, scheduler, expiration query, or EXPIRED transition implementation.

No repository method selects overdue sessions by status and timestamp.

### 2.6 Session retrieval

**Status: DOES NOT EXPIRE OR REJECT OVERDUE**

`getSessionForPlayer` only performs:

```text
find by sessionId and playerId
otherwise Session not found or access denied
```

It performs no:

- 30-minute inactivity check
- 120-minute lifetime check
- `EXPIRED` status transition
- `ended_at` update

Evidence:

- [GameSessionService.java](backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java): `getSessionForPlayer`
- [GameSessionController.java](backend/src/main/java/com/vietsuuky/session/controller/GameSessionController.java): GET endpoint delegates directly to `getSessionForPlayer`

Therefore a 200-minute-old `ACTIVE` session can be returned while retaining `ACTIVE` status.

### 2.7 Can an expired session still be used?

There is no effective expired state, so an overdue session can still be:

- Retrieved through GET
- Used for event recording while status remains `ACTIVE`
- Used for completion while status remains `ACTIVE`
- Abandoned through the abandon endpoint

For event recording and completion, `recordEvent` and `completeSession` check only `status == ACTIVE`. They do not verify activity or absolute lifetime.

Evidence:

- [GameSessionService.java](backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java): `recordEvent`, `completeSession`, and `abandonSession`

This violates the approved expired-session exclusion.

### 2.8 State transition atomicity

`recordEvent`, `completeSession`, and `abandonSession` are `@Transactional`.

For existing terminal transitions:

- Event recording requires `ACTIVE`.
- Completion requires `ACTIVE`.
- Abandonment requires `ACTIVE`.
- Completion and abandonment set `endedAt`.

However, no atomic `ACTIVE -> EXPIRED` transition exists.

Consequences:

- The EXPIRED state has no transactional implementation.
- There is no shared transition method that atomically checks the timeout and changes status.
- The entity uses `@Version`, and JPA stale updates can be rejected optimistically, but that does not implement expiration rules.
- Concurrent activity and expiration can race because no expiration check or lock participates in session operations.

### 2.9 Concurrent expiration and activity

**Status: UNSAFE / UNDEFINED**

The repository and service have no expiration-aware locking or conditional update.

The `GameSession.version` `@Version` field can prevent stale overwrites, but it does not:

- Check both inactivity and lifetime
- Mark a session `EXPIRED`
- Reject an event that began after expiration
- Guarantee that expiration wins atomically over completion or event recording

The only database concurrency protection found is the V8 partial unique index preventing duplicate ACTIVE sessions per player and mission.

## 3. Session-related migrations

V1–V8 inspection:

- V3 creates session `started_at`, `ended_at`, `status`, and `updated_at`.
- V4/V5/V6 add unrelated reward and idempotency integrity changes.
- V7 aligns mission type default.
- V8 adds partial unique index for ACTIVE sessions.

No migration creates:

- `last_activity_at`
- A session expiration timestamp
- An expiration index
- A database constraint that expires sessions

PostgreSQL has a database-side uniqueness index only, not a database-side expiration policy.

## 4. Existing tests

The session test suite does not cover expiration.

Evidence:

- [GameSessionServiceIntegrationTest.java](backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java): tests creation, completion, cross-player access, duplicate ACTIVE, locked missions, and mission read behavior
- [Phase24FoundationIntegrationTest.java](backend/src/test/java/com/vietsuuky/Phase24FoundationIntegrationTest.java): tests persistence and optimistic locking foundations

No test modifies or asserts:

- 30-minute inactivity
- 120-minute absolute lifetime
- `EXPIRED`
- `ended_at` on expiration
- Retrieval of an overdue session
- Event after inactivity
- Completion after inactivity or absolute lifetime
- Abandon after inactivity or absolute lifetime
- Concurrent expiration versus event/completion
- EXPIRED/COMPLETED/ABANDONED terminal immutability
- Scheduled expiration behavior

## 5. Answers to audit questions

1. **Is the 30-minute inactivity timeout implemented?**
   - No.

2. **Is the 120-minute absolute lifetime implemented?**
   - No.

3. **Which timestamps are persisted?**
   - `started_at`, `ended_at`, `created_at`, and `updated_at` on `GameSession`.
   - `MissionProgress.last_event_at` exists separately but is not an approved session activity source.

4. **What event updates last activity?**
   - Validated event handling updates `MissionProgress.lastEventAt`, but session expiration does not consume it.
   - Session `updatedAt` may change generically, but no dedicated inactivity logic uses it.

5. **How are expired sessions detected?**
   - They are not detected.

6. **Does session retrieval reject or expire overdue sessions?**
   - No.

7. **Can an expired session still be used?**
   - Since no overdue session becomes EXPIRED, overdue sessions can remain usable as ACTIVE for event, complete, and abandon operations.

8. **Are state transitions atomic?**
   - Existing terminal methods use `@Transactional`, but no `ACTIVE -> EXPIRED` transition exists.

9. **Are concurrent expiration and session activity handled safely?**
   - No. `@Version` is optimistic locking for stale writes, not expiration reconciliation.

10. **Are there tests for timeout boundaries and terminal-state immutability?**
    - No.

## 6. Missing functionality

- Dedicated session last-activity timestamp or a formally approved reuse of an existing timestamp.
- 30-minute inactivity check at event, retrieval, completion, abandonment, and scheduled processing boundaries.
- 120-minute absolute lifetime check.
- Atomic `ACTIVE -> EXPIRED` transition.
- Setting `endedAt` during expiration.
- Scheduled or on-demand detection of ACTIVE overdue sessions.
- Repository queries for overdue sessions.
- Exclusion of EXPIRED sessions from event, completion, abandonment, and reward workflows.
- Expiration-aware concurrent activity handling.
- Tests for timeout boundaries and terminal states.

## 7. Security and concurrency risks

### High

- Overdue sessions remain ACTIVE and can complete or generate existing reward behavior.
- Absolute lifetime can be exceeded indefinitely.
- Inactivity can be exceeded without a persistent expired state.

### Medium

- Concurrent event/completion and expiration can produce conflicting state changes.
- Using generic `updatedAt` or mission-progress `lastEventAt` would create inconsistent semantics.
- A lazy GET-only expiration check could leave other workers unaware of overdue sessions until retrieval.
- A Java-only check cannot guarantee that two concurrent operations cannot cross the timeout boundary.

### Integrity

- Terminal-state invariants are not enforced by a generic transition mechanism.
- Public entity setters permit direct state mutation outside service rules.
- No migration constraint protects expiration semantics.

## 8. Recommended minimal remediation

The remediation is a recommendation only; no implementation was performed.

1. Add a dedicated session activity timestamp through a forward-only migration. `last_activity_at` is the minimal unambiguous column.
2. Initialize `last_activity_at` to the session start time.
3. Update `last_activity_at` only when a validated session event is accepted.
4. Centralize expiration constants and the timeout calculation in session domain logic:
   - inactivity: 30 minutes
   - absolute: 120 minutes
5. Create a transactional service method that:
   - loads only `ACTIVE` sessions
   - obtains the required row or optimistic lock
   - checks inactivity and absolute lifetime
   - transitions to `EXPIRED`
   - sets `endedAt`
6. Add an expiration check before retrieval, event, completion, and abandonment.
7. Re-check expiration inside the transaction rather than relying on a controller or stale entity value.
8. Add repository conditional/locking queries for overdue sessions and scheduled expiration processing.
9. Add a scheduled expiration sweep, while retaining lazy checks to prevent stale sessions being used between sweep cycles.
10. Keep existing V1–V8 immutable and introduce a new forward-only migration only if approved.
11. Do not use `MissionProgress.lastEventAt` or generic `updatedAt` as the only session inactivity source.

## 9. Required tests

### Unit tests

- Exactly 30 minutes inactivity remains ACTIVE or expires according to the approved boundary definition.
- 30 minutes plus 1 millisecond is expired.
- Exactly 120 minutes lifetime remains ACTIVE or expires according to the approved boundary definition.
- 120 minutes plus 1 millisecond is expired.
- Inactivity and absolute lifetime both return EXPIRED consistently.
- Terminal states cannot transition to ACTIVE.
- `endedAt` is set exactly once.

### Integration tests

- Retrieval expires an overdue ACTIVE session.
- Retrieval does not allow an overdue session to remain usable.
- Event rejects an inactive/expired session.
- Completion rejects an inactive/expired session.
- Abandonment rejects an inactive/expired session.
- Scheduled sweep expires overdue sessions.
- Completed, abandoned, or expired sessions are not changed to ACTIVE.
- Expiration does not award rewards or mutate mission progress.
- Valid event refreshes inactivity timestamp but does not extend the 120-minute ceiling.

### Concurrency tests

- Concurrent event and inactivity sweep.
- Concurrent completion and inactivity sweep.
- Concurrent absolute-lifetime expiration and event.
- Concurrent completion and expiration.
- Only one operation can transition an ACTIVE session to terminal state.

### Boundary and regression tests

- Session at 29:59:59 inactivity remains ACTIVE.
- Session at 30:00:00 follows the approved boundary.
- Session at 119:59:59 lifetime remains eligible.
- Session at 120:00:00 follows the approved boundary.
- Existing owner and cross-player tests remain unchanged.

## 10. Final status

**BLOCKED**

The repository does not implement the approved 30-minute inactivity or 120-minute absolute lifetime policy.

The missing behavior is a compliance gap, not merely an untested edge case:

- No timeout detection exists.
- No expiration transition exists.
- No dedicated activity timestamp exists.
- Overdue sessions remain usable.
- Concurrent expiration/activity is undefined.
- Terminal-state immutability is only partial and has no expiration transition.

The `EXPIRED` enum and lifecycle documentation are present, but runtime behavior is absent.
