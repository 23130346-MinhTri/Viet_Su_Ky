# PHASE 2.4.2.3 — SESSION EXPIRATION FINAL VERIFICATION REPORT

## Final recommendation

**PASS WITH WARNINGS**

The verified implementation persists `ACTIVE -> EXPIRED` before rejected event/completion operations, and the PostgreSQL service-level concurrency tests pass on a disposable PostgreSQL 16 instance.

## 1. Scope verified

- Transaction rollback behavior for overdue sessions
- Persisted database state after rejected operations
- Event versus expiration scheduler
- Completion versus expiration scheduler
- Abandonment versus expiration scheduler
- Concurrent expiration attempts
- Terminal-state immutability
- Flyway V1–V9
- Hibernate `ddl-auto=validate`

No V1–V8 migration, active development database, Docker volume, score policy, reward policy, frontend, or Phaser behavior was changed.

## 2. Transaction rollback evidence

### Implementation inspected

The overdue operation methods use `@Transactional`:

- `recordEvent(...)` uses `@Transactional(noRollbackFor = ApiException.class)`
- `completeSession(...)` uses `@Transactional(noRollbackFor = ApiException.class)`
- `abandonSession(...)` uses `@Transactional(noRollbackFor = ApiException.class)`
- `getSessionForPlayer(...)` uses `@Transactional`

When an operation detects expiration, `expireIfOverdue`:

1. sets `status = EXPIRED`
2. sets `endedAt`
3. saves the entity
4. returns a lifecycle result or throws `SESSION_EXPIRED`

For event and completion, `SESSION_EXPIRED` is outside the configured rollback policy, so the transaction commits the expiration transition and rejects the operation.

For abandonment of an overdue session, the method returns the already-expired entity through a normal transaction commit.

### GET overdue session

Verified:

- `getSessionForPlayer` locks the owner/session row
- an overdue `ACTIVE` session becomes `EXPIRED`
- `endedAt` is persisted
- a subsequent repository reload returns `EXPIRED`

Test:

- `GameSessionServiceIntegrationTest#getRetrievalExpiresInactivityOverdueSession`

### Event on overdue session

Verified:

- event throws `SESSION_EXPIRED`
- no event count increment
- no session last-activity refresh
- persisted session remains `EXPIRED`
- `endedAt` remains persisted

Test:

- `GameSessionServiceIntegrationTest#eventAndCompletionRejectOverdueSessionWithoutReward`

### Completion on overdue session

Verified:

- completion throws `SESSION_EXPIRED`
- reward code is not reached
- mission progress is not completed
- persisted session remains `EXPIRED`

Test:

- `GameSessionServiceIntegrationTest#eventAndCompletionRejectOverdueSessionWithoutReward`

### Abandon overdue session

Verified:

- abandonment converts the persisted session to `EXPIRED`
- the status is not `ABANDONED`
- `endedAt` is persisted

Test:

- `GameSessionServiceIntegrationTest#abandonmentConvertsOverdueSessionToExpired`

### Stable API error mapping

`GlobalExceptionHandler` maps `SESSION_EXPIRED` to HTTP `409 Conflict`.

The exception is stable and persisted database state is independent of the controller response.

## 3. PostgreSQL concurrency tests added

Added:

- `backend/src/test/java/com/vietsuuky/Phase2423PostgresConcurrencyTest.java`

The test class is opt-in through `POSTGRES_INTEGRATION_TESTS=true` and requires:

- `POSTGRES_INTEGRATION_URL`
- `POSTGRES_INTEGRATION_USER`
- `POSTGRES_INTEGRATION_PASSWORD`

The tests use independent worker threads, CountDownLatch coordination, real Spring service transactions, pessimistic row locks, and the real expiration scheduler.

### Event versus scheduler

Result:

- final state `EXPIRED`
- `endedAt` present
- event count remains `0`

### Completion versus scheduler

Result:

- final state `EXPIRED`
- `endedAt` present
- final score remains `0`
- no reward record
- mission progress not completed

### Abandonment versus scheduler

Result:

- final state `EXPIRED`
- session is not left `ABANDONED`
- `endedAt` present

### Concurrent expiration attempts

Verified:

- scheduler and retrieval/expiration compete on independent transactions
- only `EXPIRED` wins
- `endedAt` remains stable
- subsequent event and completion attempts reject
- no reward record

### Terminal immutability

The concurrent tests also verify that:

- `EXPIRED` cannot be replaced by `ACTIVE`
- subsequent lifecycle operations cannot restore activity

## 4. PostgreSQL verification

Disposable PostgreSQL 16.13 instance:

- database: `phase2423`
- port: `55445`
- no Docker volume
- no active development database modification

Flyway:

- V1 through V9 all present
- V1–V9 `success=true`
- current version V9

Hibernate:

- `ddl-auto=validate`
- EntityManagerFactory initialized without mapping mismatch

Indexes verified:

- `idx_game_sessions_active_expiration`
- `uk_game_sessions_one_active_per_player_mission`

PostgreSQL service concurrency test result:

- 4 passed
- 0 failed
- 0 skipped
- 0 errors

## 5. Regression results

Java: `17.0.16`

Spring Boot: `3.5.3`

PostgreSQL: `16.13`

### Focused expiration tests

- `GameSessionExpirationPolicyTest`: 4 passed
- `GameSessionServiceIntegrationTest`: 10 passed

### Full Maven regression

Command:

```text
mvn -q clean test
```

Result:

- 31 passed
- 0 failed
- 0 errors
- 8 PostgreSQL tests skipped by the normal H2 profile

The 31 passes include authentication, player profile, health, mission/session, foundation, and expiration tests.

### PostgreSQL opt-in

Command pattern:

```text
mvn -q clean test "-Dtest=Phase2423PostgresConcurrencyTest"
```

With `POSTGRES_INTEGRATION_TESTS=true` and the disposable database variables set:

- 4 passed
- 0 failed
- 0 skipped

## 6. Files changed

### Added

- `backend/src/test/java/com/vietsuuky/Phase2423PostgresConcurrencyTest.java`
- `PHASE_2_4_2_3_FINAL_VERIFICATION_REPORT.md`

### Modified

- `backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java`

No production Java file or migration was changed by this verification task.

## 7. Remaining limitations

- HTTP-level integration tests for session endpoints were not added; stable `409` mapping is verified in `GlobalExceptionHandler`.
- The normal H2 suite intentionally skips PostgreSQL opt-in classes.
- The PostgreSQL suite uses real service transactions and row locks but does not introduce a distributed scheduler or additional infrastructure.
- Existing completion score/evidence behavior remains outside this verification scope.

## 8. Database final-state evidence

For rejected operation tests, database reload showed:

- overdue session status persisted as `EXPIRED`
- `endedAt` persisted
- event count unchanged
- no reward mutation
- no mission completion mutation

For PostgreSQL concurrency tests, database reload showed:

- event/scheduler race ends `EXPIRED`
- completion/scheduler race ends `EXPIRED`
- abandon/scheduler race ends `EXPIRED`
- concurrent expiration remains `EXPIRED`
- subsequent terminal operations cannot restore `ACTIVE`
