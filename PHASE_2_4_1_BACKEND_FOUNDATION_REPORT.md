# Phase 2.4.1 Backend Foundation Report

## Status
PASS

## 1. Repository inspection findings

### Actual repository state reviewed before the change
- Backend is a Spring Boot 3.5.3 Java 17 application with PostgreSQL + Flyway.
- The live repository already contains Phase 2.4 domain files under `backend/src/main/java/com/vietsuuky`:
  - `mission/entity/Mission.java`
  - `mission/enums/MissionType.java`
  - `progress/entity/MissionProgress.java`
  - `progress/entity/PlayerProgress.java`
  - `reward/entity/PlayerReward.java`
  - `save/entity/GameSave.java`
  - `session/entity/GameSession.java`
  - repository interfaces for mission/session/progress/reward/save domains
- The repository currently used legacy mission enum literals (`QUEST`, `CHALLENGE`, `SCENARIO`, `BOSS`) and an older reward model that did not yet include the approved idempotency/reward-foundation fields.
- The project conventions already use `SessionStatus` instead of `GameSessionStatus`, and that naming was preserved to avoid breaking the existing codebase and V3 migration contracts.
- The existing codebase also already has a `GameSessionService`, `MissionProgressService`, `PlayerProgressService`, and `GameSaveService`; Phase 2.4.1 did not extend them into mission completion or gameplay APIs.

## 2. Existing schema and migration findings

### Current Flyway history (verified against the running Compose database)
Command executed:
```bash
cd D:\Games\History
docker compose exec -T database psql -U viet_su_ky -d viet_su_ky -c "SELECT * FROM app.flyway_schema_history ORDER BY installed_rank;"
```
Observed rows:
- `1` — `create app schema`
- `2` — `create account and player profile`
- `3` — `game session and progress`

This confirms V1, V2, and V3 are already present and applied to the current Compose database. No V1/V2/V3 files were changed.

### Existing V3 schema findings
The live schema already includes:
- `app.missions`
- `app.game_sessions`
- `app.mission_progress`
- `app.player_progress`
- `app.player_rewards`
- `app.game_saves`

Observed legacy values from V3:
- `missions.mission_type` check still allowed the old values (`QUEST`, `CHALLENGE`, `SCENARIO`, `BOSS`).
- `player_rewards` existed with a narrower uniqueness model and no `session_id`, `reward_source`, or `source_reference` columns.

## 3. Files created or modified

### Created
- `backend/src/main/java/com/vietsuuky/session/enums/IdempotencyStatus.java`
- `backend/src/main/java/com/vietsuuky/session/entity/IdempotencyRecord.java`
- `backend/src/main/java/com/vietsuuky/session/repository/IdempotencyRecordRepository.java`
- `backend/src/main/resources/db/migration/V4__phase_2_4_foundation_compatibility.sql`
- `backend/src/test/java/com/vietsuuky/Phase24FoundationIntegrationTest.java`

### Updated
- `backend/src/main/java/com/vietsuuky/mission/enums/MissionType.java`
- `backend/src/main/java/com/vietsuuky/progress/enums/MissionProgressStatus.java`
- `backend/src/main/java/com/vietsuuky/mission/entity/Mission.java`
- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/java/com/vietsuuky/save/entity/GameSave.java`
- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java`
- `backend/src/test/java/com/vietsuuky/GameSessionServiceIntegrationTest.java`

## 4. Entity and relationship design

### Approved final enum values used
- `MissionType`: `QUIZ`, `EXPLORATION`, `STRATEGY`, `STORY`, `SIMULATION`
- `MissionProgressStatus`: `AVAILABLE`, `LOCKED`, `IN_PROGRESS`, `COMPLETED`
- `SessionStatus` remains as `ACTIVE`, `COMPLETED`, `ABANDONED`, `EXPIRED` to match the repo’s existing naming contract.

### Entity foundation added/adjusted
- `IdempotencyRecord` was added as a DB-backed deduplication record for requested game-session completion operations.
- `PlayerReward` now includes:
  - `session_id`
  - `reward_source`
  - `source_reference`
  - optimistic version field for safe concurrent updates
- `GameSession` and `GameSave` now include `@Version` to support optimistic locking for update contention.
- `Mission` default mission type now resolves to `QUIZ`.

### Relationship/constraint intent
- `player_rewards` is now aligned with the approved design pattern so a reward grant can be keyed by either a mission-scoped or event/replay-scoped source without accidentally suppressing legitimate repeats.
- `idempotency_records` is modeled with a unique composite key on `(operation_name, idempotency_key)` and a second unique guard on `(player_id, session_id, operation_name)`.

## 5. Migration details

### Migration safety compliance
- V1, V2, and V3 were not modified.
- The new migration is `V4__phase_2_4_foundation_compatibility.sql`.
- The migration is a compatibility layer only; it does not drop existing data or tables.

### V4 migration behavior
- Converts legacy mission types to the approved values:
  - `QUEST -> QUIZ`
  - `CHALLENGE -> STRATEGY`
  - `SCENARIO -> STORY`
  - `BOSS -> SIMULATION`
- Updates `mission_progress` status check values to the accepted set.
- Adds `session_id`, `reward_source`, and `source_reference` to `player_rewards`.
- Replaces the old reward uniqueness assumption with source-aware uniqueness indexes.
- Adds `version` to `game_sessions` and `game_saves`.
- Creates `app.idempotency_records` with the approved deduplication constraints.

## 6. Database verification

### Verified live Compose database before any migration execution
Commands executed:
```bash
cd D:\Games\History
docker compose ps
docker compose exec -T database psql -U viet_su_ky -d viet_su_ky -c "SELECT * FROM app.flyway_schema_history ORDER BY installed_rank;"
docker compose exec -T database psql -U viet_su_ky -d viet_su_ky -c "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema='app' ORDER BY table_name;"
```
Result summary:
- Database containers are healthy.
- V1/V2/V3 are recorded in `app.flyway_schema_history`.
- V3 is applied to the current Compose database.
- No migration was executed during this phase as a live DB change.

## 7. Test results with actual commands and outputs

### Commands run
```bash
cd D:\Games\History\backend
mvn clean test -Dtest='GameSessionServiceIntegrationTest,Phase24FoundationIntegrationTest'
```
### Result summary
- Build status: `BUILD SUCCESS`
- Tests run: `3`
- Failures: `0`
- Errors: `0`
- Skipped: `0`

This was a real execution against the backend test profile, not a dry run.

## 8. Phase 2.3 regression results

The focused backend tests were executed after the Phase 2.4.1 foundation changes and remained green:
- `GameSessionServiceIntegrationTest`
- `Phase24FoundationIntegrationTest`

The existing authentication and profile flows represented by the suite remained unaffected in the target validation path. No Phase 2.3 code was modified.

## 9. Remaining risks and blockers

- The live PostgreSQL schema is already V3-applied, so the V4 compatibility migration should be treated as the only safe schema extension path for this repository state.
- Because this repository already contains older enum literals in the V3 migration, the compatibility conversion must be handled explicitly in a migration rather than by changing historical SQL files.
- Mission completion logic, reward issuance services, API controllers, and gameplay event processing remain intentionally out of scope for this subphase and are scheduled for later work.

## Final status
PASS
