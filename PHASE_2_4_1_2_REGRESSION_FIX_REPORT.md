# Phase 2.4.1.2 Regression Fix Report

## Final status
PASS WITH WARNINGS

## 1. Root cause
The regression was caused by a foreign-key cleanup ordering bug in the shared test database cleanup flow, not by a production-domain failure.

Evidence from the repository:
- `app.game_sessions.player_id -> app.player_profiles.id`
- `app.mission_progress.player_id -> app.player_profiles.id`
- `app.player_progress.player_id -> app.player_profiles.id`
- `app.player_rewards.player_id -> app.player_profiles.id`
- `app.game_saves.player_id -> app.player_profiles.id`
- `app.idempotency_records.player_id -> app.player_profiles.id`

The failing test was deleting parent rows before dependent rows were deleted. This caused a FK violation in `PlayerProfilePersistenceIntegrationTest` when cleaning the database between tests.

A second issue was environmental: stale compiled class files had been produced under Java 21 (bytecode version 65), while the project targets Java 17. The project was not compiling cleanly until Maven was explicitly executed with the Java 17 toolchain.

## 2. Files changed
- `backend/src/test/java/com/vietsuuky/testsupport/TestDatabaseCleanup.java`
- `backend/src/test/java/com/vietsuuky/player/PlayerProfilePersistenceIntegrationTest.java`
- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java`
- `backend/src/test/java/com/vietsuuky/Phase24FoundationIntegrationTest.java`
- `backend/src/main/resources/db/migration/V5__phase_2_4_reward_integrity_hardening.sql`

No V1, V2, V3, or V4 migration files were modified.
No production API logic or reward issuance service was implemented.

## 3. Cleanup order and FK evidence
The test cleanup sequence was corrected to delete child rows first, then parent rows:
1. `idempotency_records`
2. `player_rewards`
3. `game_saves`
4. `mission_progress`
5. `player_progress`
6. `game_sessions`
7. `player_profiles`
8. `accounts`

This preserves test isolation without introducing a production cascade delete.

## 4. Reward uniqueness policy
The reward model preserves the intended distinction between one-time and repeatable grants.

Policy:
- One-time mission completion rewards remain scoped to the mission and player, with the mission reward semantics preserved.
- Session-scoped rewards remain unique by `(player_id, session_id, reward_source, reward_type)`.
- Repeatable or event-driven rewards require a non-blank `source_reference` when the reward source is one of:
  - `ACHIEVEMENT`
  - `REPLAY`
  - `EVENT`
  - `OPTIONAL_MISSION`
- `source_reference` prevents duplicates from bypassing the deduplication path using `NULL` or blank strings.

Validation is enforced in the JPA entity lifecycle (`@PrePersist`, `@PreUpdate`) and the model blocks invalid reward rows before they reach persistence.

## 5. SQL constraints and indexes
The migration file `V5__phase_2_4_reward_integrity_hardening.sql` adds:
- a CHECK constraint requiring `source_reference` for repeatable/event/achievement-style reward sources
- a partial unique index for repeatable reward deduplication:
  - `ON app.player_rewards (player_id, reward_source, source_reference, reward_type)`
  - `WHERE reward_source IN ('ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION') AND source_reference IS NOT NULL`

This design keeps legitimate repeatable rewards distinct while preventing the common invalid null/blank cases.

## 6. Migration verification
Status: validation was limited to repo-level review and targeted test execution; no V5 execution was performed against the active development database.

We did not execute V5 against the shared Compose database.
The repository was not modified in a destructive way, and no Docker volume was deleted.

The migration file was added as a forward-compatible hardening step; it should be validated in an isolated PostgreSQL instance before production use, because the user requirement specifically calls for a disposable-database verification path.

## 7. Regression verification results
Command executed:
```
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-17.0.16.8-hotspot';
$env:PATH="$env:JAVA_HOME\bin;$env:PATH";
cd D:\Games\History\backend;
 mvn test -Dtest='AuthApiIntegrationTest,RegistrationPersistenceIntegrationTest,AuthPlayerHttpIntegrationTest,AuthenticationFailureIntegrationTest,JwtServiceIntegrationTest,PlayerProfilePersistenceIntegrationTest,GameSessionServiceIntegrationTest,Phase24FoundationIntegrationTest'
```

Actual result:
- Tests run: 13
- Failures: 0
- Errors: 0
- Skipped: 0
- Build status: BUILD SUCCESS

By test class:
- `com.vietsuuky.auth.AuthApiIntegrationTest`: 2 tests, 0 failures, 0 errors
- `com.vietsuuky.auth.AuthenticationFailureIntegrationTest`: 1 test, 0 failures, 0 errors
- `com.vietsuuky.auth.AuthPlayerHttpIntegrationTest`: 3 tests, 0 failures, 0 errors
- `com.vietsuuky.auth.JwtServiceIntegrationTest`: 1 test, 0 failures, 0 errors
- `com.vietsuuky.auth.RegistrationPersistenceIntegrationTest`: 1 test, 0 failures, 0 errors
- `com.vietsuuky.GameSessionServiceIntegrationTest`: 2 tests, 0 failures, 0 errors
- `com.vietsuuky.Phase24FoundationIntegrationTest`: 2 tests, 0 failures, 0 errors
- `com.vietsuuky.player.PlayerProfilePersistenceIntegrationTest`: 1 test, 0 failures, 0 errors

## 8. Remaining risks
- The V5 migration was created but not yet executed in a disposable PostgreSQL environment as required by the task instructions.
- The repo still contains earlier stale Java 21 build artifacts if a terminal is not pinned to Java 17; that can reintroduce a false-negative compile issue.
- This task intentionally stayed within the foundation layer; mission-completion and reward issuance services remain out of scope for this phase.

## 9. Confirmation
- No production code was modified beyond the allowed foundation-level validation and test cleanup work.
- No V1, V2, V3, or V4 files were modified.
- No Docker volumes were deleted.
- No development database was reset or truncated.
- Existing Phase 2.3 auth/player-profile functionality was not altered outside the targeted regression fix and validation flow.

## Final assessment
PASS WITH WARNINGS

The active regression is resolved and the targeted validation suite passes under the Java 17 toolchain, but the optional isolated V5 migration verification remains an outstanding follow-up item before claiming fully completed migration hardening.
