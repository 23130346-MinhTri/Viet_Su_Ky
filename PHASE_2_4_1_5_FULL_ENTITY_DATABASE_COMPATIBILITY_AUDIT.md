# Phase 2.4.1.5 — Full Entity-Database Compatibility Audit

**Final status: BLOCKED**

Two entity-to-schema differences prevent Hibernate validation against the unmodified V1–V6 schema: `GameSession.version` has the wrong Java width, and `PlayerReward.version` has no database column. The other mapped columns were accepted by Hibernate after those two differences were isolated. A separate catalog warning is that the V1–V6 `missions.mission_type` default is `QUEST`, which violates the post-V4 check constraint.

This is an audit only. No production Java, test, configuration, or migration file was changed. V1–V6 remain unchanged. The only repository file created for this phase is this report.

## 1. Scope and entity inventory

All nine JPA entities discovered under `backend/src/main/java` were reviewed. Hibernate also created the Flyway bookkeeping table; it is not a JPA entity.

In the inventory below, `?` marks a nullable database column. A relationship field maps to the indicated foreign-key UUID column. All columns not marked nullable are `NOT NULL`. PostgreSQL timestamp columns are `timestamp(6) without time zone`.

| Entity / table | Fields: Java type -> PostgreSQL column type |
|---|---|
| `Account` / `app.accounts` | `id UUID -> uuid`; `email String -> varchar(255)`; `passwordHash String -> varchar(255)`; `status AccountStatus (STRING) -> varchar(30)`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `PlayerProfile` / `app.player_profiles` | `id UUID -> uuid`; `account Account -> account_id uuid`; `displayName String -> varchar(50)`; `avatarUrl String? -> text?`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `Mission` / `app.missions` | `id UUID -> uuid`; `code String -> varchar(100)`; `title String -> varchar(150)`; `description String? -> text?`; `chapter String -> varchar(100)`; `missionOrder Integer -> int4`; `missionType MissionType (STRING) -> varchar(30)`; `unlockCondition String? -> varchar(255)?`; `prerequisiteMission Mission? -> prerequisite_mission_id uuid?`; `rewardPoints int -> int4`; `rewardCode String? -> varchar(100)?`; `status MissionStatus (STRING) -> varchar(20)`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `GameSession` / `app.game_sessions` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `mission Mission -> mission_id uuid`; `startedAt Instant -> timestamp`; `endedAt Instant? -> timestamp?`; `status SessionStatus (STRING) -> varchar(30)`; `eventCount int -> int4`; `finalScore int -> int4`; `metadata String? -> text?`; `version Long (@Version) -> int4`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `MissionProgress` / `app.mission_progress` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `mission Mission -> mission_id uuid`; `status MissionProgressStatus (STRING) -> varchar(30)`; `progressValue int -> int4`; `startedAt Instant? -> timestamp?`; `completedAt Instant? -> timestamp?`; `lastEventAt Instant? -> timestamp?`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `PlayerProgress` / `app.player_progress` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `chapter String -> varchar(100)`; `totalMissionsCompleted int -> int4`; `totalPoints int -> int4`; `unlockedContent String -> text`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `PlayerReward` / `app.player_rewards` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `mission Mission -> mission_id uuid`; `session GameSession? -> session_id uuid?`; `rewardSource String -> varchar(50)`; `sourceReference String? -> varchar(128)?`; `rewardType RewardType (STRING) -> varchar(30)`; `rewardPoints int -> int4`; `rewardCode String? -> varchar(100)?`; `status String (implicit JPA length 255) -> varchar(20)`; `claimedAt Instant -> timestamp`; `version Long (@Version) -> **missing**`; `createdAt Instant -> timestamp` |
| `GameSave` / `app.game_saves` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `mission Mission? -> mission_id uuid?`; `session GameSession? -> session_id uuid?`; `saveName String -> varchar(100)`; `saveData String -> text`; `saveSize int -> int4`; `version Integer (@Version) -> int4`; `createdAt Instant -> timestamp`; `updatedAt Instant -> timestamp` |
| `IdempotencyRecord` / `app.idempotency_records` | `id UUID -> uuid`; `player PlayerProfile -> player_id uuid`; `mission Mission? -> mission_id uuid?`; `session GameSession? -> session_id uuid?`; `operationName String -> varchar(80)`; `idempotencyKey String -> varchar(255)`; `requestHash String -> text`; `responseJson String? -> text?`; `status IdempotencyStatus (STRING) -> varchar(20)`; `createdAt Instant -> timestamp`; `completedAt Instant? -> timestamp?` |

`GenerationType.UUID` is used for each entity ID; PostgreSQL also has `gen_random_uuid()` defaults. Hibernate supplies entity IDs, so the database defaults are an acceptable alternative for inserts that omit the ID.

## 2. PostgreSQL schema inspection evidence

### Disposable database and migration run

- Database: PostgreSQL **16.13**, image `postgres:16-alpine`.
- Container: `phase2415-audit-pg16`, bound only to `127.0.0.1:55441`.
- Storage: container-local `tmpfs` at `/var/lib/postgresql/data`; no Docker volume was mounted. The container was removed after inspection. No active development database was connected to or modified, and no existing container was changed.
- Spring Boot 3.5.3 / Hibernate ORM 6.6.18.Final / Java 17.0.16.
- The initial application run used the configured `ddl-auto=validate`; Flyway applied V1, V2, V3, V4, V5, and V6 successfully. The catalog showed successful version rows 1–6.
- The initial database contained the nine entity tables plus `app.flyway_schema_history`.

The actual catalog was inspected using `information_schema.columns`, `pg_constraint` with `pg_get_constraintdef`, and `pg_indexes`. In particular, the catalog returned:

| Table / column | Actual catalog value |
|---|---|
| `game_sessions.version` | `int4`, `NOT NULL`, default `0` |
| `game_saves.version` | `int4`, `NOT NULL`, default `1` |
| `player_rewards.version` | No column |
| `missions.mission_type` | `varchar(30)`, `NOT NULL`, default `'QUEST'`; actual check allows only `QUIZ`, `EXPLORATION`, `STRATEGY`, `STORY`, `SIMULATION` |
| `player_rewards.status` | `varchar(20)`, `NOT NULL`; check allows `CLAIMED` and `REDEEMED` |
| `player_rewards.reward_source` | `varchar(50)`, `NOT NULL`; check allows `MISSION_COMPLETION`, `ACHIEVEMENT`, `REPLAY`, `EVENT`, `OPTIONAL_MISSION` |

Other catalog findings:

- All nine tables are in schema `app`, and every entity explicitly maps to that schema.
- Primary keys are `id` UUIDs on all nine entity tables.
- Column nullability, non-version PostgreSQL types, and named relationship columns match the entity mappings. PostgreSQL timestamp precision is 6; Hibernate accepted the Java `Instant` mappings in the staged validation described below.
- `@Enumerated(EnumType.STRING)` mappings agree with the database check constraints for `AccountStatus`, `MissionType`, `MissionStatus`, `SessionStatus`, `MissionProgressStatus`, `RewardType`, and `IdempotencyStatus`. PostgreSQL stores these as varchar, not native enum types.
- Foreign keys and delete actions in the catalog are: `player_profiles.account_id -> accounts.id ON DELETE CASCADE`; `missions.prerequisite_mission_id -> missions.id ON DELETE SET NULL`; game-session player cascade / mission restrict; mission-progress player and mission cascade; player-progress player cascade; reward player cascade / mission restrict / session set null; game-save player cascade / mission and session set null; idempotency player cascade / mission and session set null. Their columns match the entity joins. JPA does not express or validate the database delete actions.
- Unique constraints/indexes are present for account email, profile account, mission code, `(player_id, mission_id)` mission progress, unique player progress, `(player_id, save_name)` saves, and both idempotency keys. V5's three player-reward unique indexes exist with the columns declared by `PlayerReward`'s `@Table(uniqueConstraints=...)`; they are indexes rather than `pg_constraint` unique constraints. Their uniqueness behavior is preserved.
- Secondary indexes found include the account lower-case email index; profile account and display-name indexes; mission status/order and chapter/order; game-session player/status/time and mission/status; mission-progress player/status and mission/status; player-progress points; player-reward player/time; game-save player/time; and idempotency player/operation/time.
- Check constraints for account, mission, session, progress, reward type/status/source, reward source-reference nonblankness, and idempotency status are present in PostgreSQL. They are additional database enforcement, not Hibernate-validated metadata.

### Default values

The catalog defaults were also compared to entity initialization and JPA-generated inserts:

- Defaults for account status; mission chapter/reward points/status; game-session status/counters/version; progress statuses/counters; player-progress chapter/counters/content; reward type/points/status/source/timestamps; save size; and idempotency status agree with the domain defaults or are database-generated timestamps/IDs.
- `missions.mission_type` retains the V3 default `'QUEST'`. V4 changed existing values and the check constraint but did **not** change the default. Since `QUEST` is excluded by the current check, an SQL insert that omits `mission_type` fails. The application initializes the field to `QUIZ` and supplies it on JPA inserts.
- `game_saves.version` has database default `1`, while `GameSave.version` initializes to `0`. This is not a Hibernate insert conflict: Hibernate includes the entity version value. The focused PostgreSQL test observed initial version `0`, increment to `1`, and stale-update rejection. The database default is used only by an insert that omits the column.

## 3. Full compatibility matrix

The following table includes every discrepancy or relevant representational difference found. Fields not listed have matching column type, length, nullability, and relationship-column mapping as shown in the entity inventory.

| Entity | Field | Java type | PostgreSQL type | Issue | Recommended correction | Migration required |
|---|---|---|---|---|---|---|
| `GameSession` | `version` | `Long` (`@Version`) | `int4 NOT NULL DEFAULT 0` | **BLOCKER** — Hibernate expects `bigint`; PostgreSQL has `integer`. The exact startup error is below. | Change the Java field to `Integer`; retain `@Version` and initialize to `0`. The schema is already `INTEGER`, and session versions are small counters. | No |
| `PlayerReward` | `version` | `Long` (`@Version`) | Missing | **BLOCKER** — Hibernate validation reports the mapped column missing. Reward records are currently inserted once and read; no service/repository operation updates reward state after issuance. | Remove the `version` field and `@Version` rather than adding unneeded optimistic locking. Revisit only when reward updates and their concurrency requirements are implemented. | No |
| `Mission` | `missionType` / database default | `MissionType` (`STRING`), initialized `QUIZ` | `varchar(30) DEFAULT 'QUEST'`, check excludes `QUEST` | **WARNING** — the default is internally inconsistent with the V4 check and breaks SQL inserts relying on that default. JPA currently supplies `QUIZ`. | In a new forward-only migration, set the default to `QUIZ`. Do not edit V1–V6. | Yes, for the recommended schema-default repair |
| `GameSave` | `version` initial/default | `Integer` (`@Version`), initialized `0` | `int4 NOT NULL DEFAULT 1` | **ACCEPTABLE DIFFERENCE** — Hibernate writes explicit `0`, the database default does not override it, and version increment/stale rejection passed. Direct SQL inserts that omit the field start at 1 instead. | Leave as-is for the current JPA-only write path. Consider changing the default only if direct database inserts are supported and should share JPA's initial value. | No for current behavior; optional only for non-JPA writers |
| `PlayerReward` | `status` | `String` (JPA default length 255) | `varchar(20)` plus check allowing only `CLAIMED`, `REDEEMED` | **WARNING / non-blocking** — declared string width differs, but the database check restricts accepted values to short values. Hibernate validation does not report this width difference. | Set `@Column(length = 20)` to accurately reflect the schema. No database change is needed. | No |
| `PlayerReward` | `rewardSource` / status values | `String` | `varchar(50)` plus database check | **ACCEPTABLE DIFFERENCE** — the entity uses strings and lifecycle validation; PostgreSQL additionally restricts values via checks. Invalid values can be rejected at flush by the database rather than by a Java enum. Existing service defaults and V5/V6 policies are preserved. | Do not alter the reward uniqueness/check policies in this phase. A Java enum/explicit whitelist is optional future domain hardening, not needed for schema compatibility. | No |

There are no other discovered Java/PostgreSQL type, nullability, primary-key, relationship join-column, enum-value, or precision mismatches. Hibernate schema validation does not compare database defaults, check constraints, foreign-key delete actions, index definitions, or uniqueness semantics; these were checked directly in the PostgreSQL catalog instead.

## 4. Complete Hibernate validation errors and startup result

### Original V1–V6 schema

The initial `ddl-auto=validate` Spring Boot startup applied all six migrations, then failed while creating the `EntityManagerFactory` with:

```text
Failed to initialize JPA EntityManagerFactory:
[PersistenceUnit: default] Unable to build Hibernate SessionFactory;
nested exception is org.hibernate.tool.schema.spi.SchemaManagementException:
Schema-validation: wrong column type encountered in column [version]
in table [app.game_sessions]; found [int4 (Types#INTEGER)],
but expecting [bigint (Types#BIGINT)]
```

This is fail-fast: the original schema-validation run reports this first mismatch and does not continue to `PlayerReward`.

### Staged diagnostic against the disposable database only

To reveal all fail-fast errors without changing repository code or migrations, the already-migrated disposable database was altered only for diagnosis:

1. Temporarily widened `app.game_sessions.version` to `BIGINT`, matching the current Java field. The next `ddl-auto=validate` run reported:

   ```text
   Schema-validation: missing column [version] in table [app.player_rewards]
   ```

2. Temporarily added `app.player_rewards.version BIGINT NOT NULL DEFAULT 0`. With both current entity mappings satisfied, Hibernate initialized the `EntityManagerFactory`; Flyway remained at V6 and reported no pending migrations.
3. Spring Boot then fully started on a non-conflicting port (18081); `/actuator/health` returned HTTP 200. Port 8080 was already occupied by an unrelated running service, so the successful diagnostic startup used 18081.

The two temporary SQL adjustments were made only in the disposable tmpfs-backed database, then the container was removed. They are not recommended schema changes and are not represented in any migration. This staged run confirms there are no additional Hibernate column-validation blockers among the nine entities after the two identified mappings are satisfied. It does not claim that the unmodified V1–V6 schema passes validation.

### GameSave persistence and optimistic locking

`Phase24FoundationIntegrationTest` was run against the disposable PostgreSQL 16 database with `ddl-auto=none` after the V1–V6 migration run. Result: **3 tests passed, 0 failures, 0 errors**. The test asserts a new save's Hibernate version is `0`, an update increments it, and a stale update raises an optimistic-lock exception. The PostgreSQL update succeeded from version `0`, proving the database default `1` did not override the value Hibernate inserted.

No `ddl-auto=update` or `ddl-auto=create` run was used.

## 5. Version-field analysis

### `GameSave.version`

- Java `Integer` and PostgreSQL `int4` are type-compatible; `@Version` is active.
- New JPA saves start at `0`. Hibernate includes the version column in the insert, so PostgreSQL's `DEFAULT 1` is not used.
- Updates increment the version, and the PostgreSQL-backed test confirmed a stale update is rejected.
- The different database default affects only writers that omit `version`. It is not a blocker for the current Hibernate write path; do not change the working optimistic-lock behavior.

### `GameSession.version`

- Java currently uses `Long`, so Hibernate expects PostgreSQL `bigint`; the actual V4 column is `INTEGER NOT NULL DEFAULT 0`.
- The existing implementation increments the version as a normal optimistic-lock counter when session rows are updated. The schema deliberately chose `INTEGER`, and a 32-bit counter is ample for per-session event updates.
- The smallest compatible correction is Java `Long` -> `Integer`, retaining `@Version` and initial value zero. No database migration or data conversion is needed.

### `PlayerReward.version`

- Java declares `Long @Version`; the catalog has no `player_rewards.version` column, so the mapping cannot validate.
- The current reward service creates rewards and returns existing rewards; the reward repository exposes reads/existence checks and no reward mutation method. The reward status is initialized on insertion and is not changed by current operations.
- Concurrent issuance is an insert/uniqueness concern. The existing V5 reward unique indexes enforce the intended uniqueness policies in the database; `@Version` would not prevent two concurrent duplicate inserts.
- Therefore optimistic locking is not currently justified. Removing this unused field is safer and smaller than a new column/migration. This conclusion is based on current operations, not just validation failure.
- If reward redemption or other concurrent reward mutation is added later, reassess and, if justified, add a version column through a forward migration with backfill/default and concurrency tests. Do not silently rely on last-write-wins for new mutable workflows.

## 6. Recommended minimal remediation plan

1. Change `GameSession.version` from `Long` to `Integer`, retaining `@Version`. No DB change is required.
2. Remove `PlayerReward.version` and `@Version`, since current rewards are insert-only and unique indexes already guard issuance uniqueness.
3. Add a forward-only V7 migration to change the `missions.mission_type` default to `QUIZ`. This changes only the default; it does not rewrite existing mission rows. Leave V1–V6 untouched.
4. Align `PlayerReward.status` metadata with the existing column using `@Column(length = 20)`. No schema change is required.
5. Do not change `GameSave.version` or its database default for the current JPA-only flow. Preserve its tested optimistic-lock behavior.
6. Re-run a fresh PostgreSQL 16 database through V1–V7 and run the complete application with `ddl-auto=validate`. Add/retain focused tests for GameSession version insert/update and GameSave optimistic locking. Keep authentication/profile behavior and all V5/V6 reward uniqueness and reference constraints unchanged.

## 7. Risks and migration impact

- The two blockers currently prevent normal application startup with the V1–V6 database. Until corrected, the application cannot complete `EntityManagerFactory` initialization.
- The GameSession correction has no data or migration impact because the existing database column and values are already 32-bit integers.
- Removing the unused PlayerReward version field avoids introducing a concurrency contract or touching existing reward rows. If future reward updates are concurrent, this decision must be revisited before implementation.
- The V7 default correction preserves all existing records and fixes a broken database default; it does not weaken the mission-type check or alter mission values.
- The `GameSave` database default can yield a different initial version for direct SQL writers. Current application persistence has demonstrated explicit JPA versioning; external writers should either provide a version or be covered by an optional forward default correction.
- `PlayerReward.status` width metadata is wider than the database column, but the current database check allows only the two short values. Matching the annotation is low-risk and requires no data migration.
- Authentication, profile persistence, and reward uniqueness policies need no functional change as part of this remediation.

## 8. Exact files for the next phase

Required or recommended:

- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java` — change the version field type to `Integer`.
- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java` — remove the unused version field/imports and set `status` column length to 20.
- **New file:** `backend/src/main/resources/db/migration/V7__phase_2_4_1_entity_schema_alignment.sql` — set the `missions.mission_type` default to `QUIZ`. Do not alter V1–V6.

No change is recommended to `backend/src/main/java/com/vietsuuky/save/entity/GameSave.java`, authentication/profile files, or reward uniqueness migrations. The optional `game_saves.version` default correction should be added to V7 only if non-JPA writers are confirmed to require the same initial value.

## Final status

**BLOCKED** — two blocking entity/schema version mismatches remain on the unmodified V1–V6 schema. The staged diagnostic confirms that satisfying those two mappings allows Hibernate validation and full application startup; remediation is intentionally not implemented in this phase.
