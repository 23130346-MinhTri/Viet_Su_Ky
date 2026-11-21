# Phase 2.4.1.1 Verification Report

## Status
BLOCKED

## Scope
This pass was limited to verification of the approved Phase 2.4.1 foundation implementation and migration work. No new business functionality was added. No V1/V2/V3 files were modified. No V4 migration was executed against the active development database. The isolated PostgreSQL verification database was used only for migration validation.

## 1. V4 SQL audit findings

### Baseline preserved
- V1, V2, and V3 remain unchanged in the repository.
- `backend/src/main/resources/db/migration/V1__create_app_schema.sql` remains the original schema bootstrap.
- `V2__create_account_and_player_profile.sql` remains the original account/profile schema.
- `V3__game_session_and_progress.sql` remains the original game-session/progress schema.
- The live Compose development database was not modified during this verification pass.

### V4 migration contents reviewed
The migration at `backend/src/main/resources/db/migration/V4__phase_2_4_foundation_compatibility.sql` performs the approved compatibility work:
- converts legacy mission types:
  - `QUEST -> QUIZ`
  - `CHALLENGE -> STRATEGY`
  - `SCENARIO -> STORY`
  - `BOSS -> SIMULATION`
- normalizes `mission_progress.status` to:
  - `AVAILABLE`
  - `LOCKED`
  - `IN_PROGRESS`
  - `COMPLETED`
- adds `session_id`, `reward_source`, and `source_reference` to `app.player_rewards`
- replaces the older reward uniqueness assumption with source-aware uniqueness indexes
- adds optimistic-lock columns to `app.game_sessions` and `app.game_saves`
- creates `app.idempotency_records` with the approved idempotency constraints

### SQL constraint review
The migration created the following key database objects:
- `uk_player_rewards_mission_reward` on `(player_id, mission_id, reward_source, reward_type)`
- `uk_player_rewards_repeatable` on `(player_id, reward_source, source_reference, reward_type)`
- `uk_player_rewards_session_reward` on `(player_id, session_id, reward_source, reward_type)`
- `uk_idempotency_records_operation` on `(operation_name, idempotency_key)`
- `uk_idempotency_records_session` on `(player_id, session_id, operation_name)`

These align with the intended separation between one-time and repeatable reward issuance and with idempotent mission completion deduplication.

## 2. Isolated migration execution results

### Command executed
```powershell
cmd /c "cd /d D:\Games\History\backend && mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:55432/verify -Dflyway.user=verify -Dflyway.password=verify -Dflyway.locations=filesystem:src/main/resources/db/migration -Dflyway.schemas=app -Dflyway.defaultSchema=app"
```

### Result
```text
[INFO] --- flyway:11.8.2:migrate (default-cli) @ viet-su-ky-backend ---
[INFO] Database: jdbc:postgresql://localhost:55432/verify (PostgreSQL 16.13)
[INFO] Schema history table "app"."flyway_schema_history" does not exist yet
[INFO] Successfully validated 4 migrations (execution time 00:00.047s)
[INFO] Creating Schema History table "app"."flyway_schema_history" ...
[INFO] Current version of schema "app": << Empty Schema >>
[INFO] Migrating schema "app" to version "1 - create app schema"
[WARNING] DB: schema "app" already exists, skipping (SQL State: 42P06 - Error Code: 0)
[INFO] Migrating schema "app" to version "2 - create account and player profile"
[INFO] Migrating schema "app" to version "3 - game session and progress"
[INFO] Migrating schema "app" to version "4 - phase 2 4 foundation compatibility"
[INFO] Successfully applied 4 migrations to schema "app", now at version v4 (execution time 00:00.490s)
[INFO] BUILD SUCCESS
```

### Flyway history check
```sql
SELECT version, description, installed_on
FROM app.flyway_schema_history
ORDER BY installed_rank;
```

Observed rows:
- `1` — `create app schema`
- `2` — `create account and player profile`
- `3` — `game session and progress`
- `4` — `phase 2 4 foundation compatibility`

Conclusion: V4 successfully executes against an isolated PostgreSQL database and is recorded in Flyway history without touching the active development database.

## 3. Entity-schema mapping results

### Review of actual Java entities vs. SQL schema
The main mapping targets match the migration schema:
- `GameSession` maps `status` to `SessionStatus`, includes `@Version`, and matches the SQL `version` column expected by V4.
- `GameSave` maps `version` and matches the `game_saves.version` column added by V4.
- `PlayerReward` contains the reward-source fields used by the migration:
  - `session_id`
  - `reward_source`
  - `source_reference`
  - `@Version`
- `IdempotencyRecord` maps to the `app.idempotency_records` table and carries the same unique constraints declared in the SQL migration:
  - `@UniqueConstraint(columnNames = {"operation_name", "idempotency_key"})`
  - `@UniqueConstraint(columnNames = {"player_id", "session_id", "operation_name"})`

### Observed mismatch / risk
- The `PlayerReward` entity declares `mission_id` as `nullable = false`, and the V4 migration also keeps `mission_id` non-nullable. This is consistent with the current schema but means the reward model is still mission-centric for one-time mission completion; repeatable event/replay rewards must use `reward_source` and `source_reference` rather than relying on the mission foreign key.
- The Postgres unique index for `source_reference` allows multiple NULL values in the same `(player_id, reward_source, source_reference, reward_type)` set. This is acceptable for some repeatable reward patterns but is still a design risk if the business rules require `source_reference` to be mandatory for repeatable rewards.

## 4. Reward uniqueness verification

### Unique indexes in isolated database
```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE schemaname='app'
  AND tablename IN ('player_rewards', 'idempotency_records')
ORDER BY tablename, indexname;
```

Observed indexes:
- `uk_player_rewards_mission_reward` on `(player_id, mission_id, reward_source, reward_type)`
- `uk_player_rewards_repeatable` on `(player_id, reward_source, source_reference, reward_type)`
- `uk_player_rewards_session_reward` on `(player_id, session_id, reward_source, reward_type)`

This is aligned with the approved design direction: one-time mission rewards, repeatable rewards, and session-scoped rewards are separated by source and reference rather than collapsed into a single generic uniqueness rule.

### Remaining risk
Repeatable reward uniqueness depends on `source_reference` being non-null for legitimate replay or event records. With `NULL` values, PostgreSQL treats `NULL` as distinct in unique indexes, so the database will not prevent duplicates if the application sets `source_reference` to `NULL` for multiple awards of the same type.

## 5. Idempotency constraint verification

The isolated database confirms:
- `uk_idempotency_records_operation` on `(operation_name, idempotency_key)`
- `uk_idempotency_records_session` on `(player_id, session_id, operation_name)`

This matches the approved semantics of:
- same idempotency key + same request -> same result reused
- same idempotency key + different request -> conflict
- different key + already completed session -> conflict
- one successful completion transaction per session/operation under unique constraints and transactional handling

## 6. Phase 2.3 regression test results

### Relevant test classes executed
Command executed:
```bash
cd D:\Games\History\backend
mvn test -Dtest='AuthApiIntegrationTest,RegistrationPersistenceIntegrationTest,AuthPlayerHttpIntegrationTest,AuthenticationFailureIntegrationTest,JwtServiceIntegrationTest,PlayerProfilePersistenceIntegrationTest,GameSessionServiceIntegrationTest,Phase24FoundationIntegrationTest'
```

Actual observed results:
- `com.vietsuuky.auth.AuthApiIntegrationTest` — 2 tests, 0 failures, 0 errors
- `com.vietsuuky.auth.AuthenticationFailureIntegrationTest` — 1 test, 0 failures, 0 errors
- `com.vietsuuky.auth.AuthPlayerHttpIntegrationTest` — 3 tests, 0 failures, 0 errors
- `com.vietsuuky.auth.JwtServiceIntegrationTest` — 1 test, 0 failures, 0 errors
- `com.vietsuuky.auth.RegistrationPersistenceIntegrationTest` — 1 test, 0 failures, 0 errors
- `com.vietsuuky.GameSessionServiceIntegrationTest` — 2 tests, 0 failures, 0 errors
- `com.vietsuuky.Phase24FoundationIntegrationTest` — 1 test, 0 failures, 0 errors
- `com.vietsuuky.player.PlayerProfilePersistenceIntegrationTest` — 1 test, 0 failures, 1 error

### Aggregate result
- Total tests run: 12
- Failures: 0
- Errors: 1
- Skipped: 0

### Actual failing error
The failing test is:
```text
PlayerProfilePersistenceIntegrationTest.profileUpdateShouldPersistAndBeReturnedByReadEndpoint
```

Observed root cause:
```text
Referential integrity constraint violation: "FK34nxn6sultnb6vbe6qxmljljj: app.game_sessions FOREIGN KEY(player_id) REFERENCES app.player_profiles(id)"
```

This occurs in `cleanDatabase()` when the test calls:
```java
playerProfileRepository.deleteAll();
accountRepository.deleteAll();
```

The cleanup order is wrong because `app.game_sessions` still contains rows referencing the same player profile. The test must delete dependent session/save records before deleting the profile, or configure cascade behavior appropriately.

## 7. Issues found and fixes, if any

### Issues found
- The migration itself is valid on an isolated PostgreSQL database and V4 records correctly in Flyway history.
- The blocker is a pre-existing test cleanup ordering issue in `PlayerProfilePersistenceIntegrationTest`.
- This issue is not a V4 migration failure; it is a data-integrity cleanup issue in the Phase 2.3 regression tests.

### Fixes implemented
No production or test code was modified during this verification-only task because the instructions for this phase explicitly required verification without starting Phase 2.4.2 or changing production behavior. The verification stop condition was reached once the blocker was confirmed.

## 8. Files modified
- Created: `PHASE_2_4_1_1_VERIFICATION_REPORT.md`
- No backend source files were modified during this verification pass.
- No migration files were modified during this verification pass.
- No development database was altered.

## 9. Remaining risks and blockers
- The repo has a failing Phase 2.3 regression path due to foreign-key cleanup ordering in a profile test.
- This prevents a clean PASS status for the current codebase even though the isolated V4 migration itself validates correctly.
- The reward-source uniqueness model still needs explicit enforcement at the application layer for non-null `source_reference` values if repeatable reward deduplication is required.
- The existing live Compose database was not modified, and Flyway verification was performed only on the isolated disposable database.

## Final verdict
The migration foundation passes isolated Flyway verification, but the repository as currently checked out is not ready to proceed because the Phase 2.3 regression suite still contains a blocking foreign-key cleanup error. Therefore, the verification phase is BLOCKED and should not proceed to Phase 2.4.2 until the regression issue is fixed or explicitly waived.
