# Phase 2.4.1.4 — PostgreSQL Schema Alignment Verification

**Final status: BLOCKED**

## 1. Root cause

`GameSave.version` was mapped as Java `Long`, while PostgreSQL defines
`app.game_saves.version` as `INTEGER` (`int4`). V4 introduced that database
column; V1 and V3 create the table without it. V5 and V6 do not change it.
PostgreSQL `INTEGER` is represented by Java `Integer`, so the entity mapping
was corrected without changing the database or existing data.

## 2. Relevant entity and migration evidence

- V4 adds `app.game_saves.version INTEGER NOT NULL DEFAULT 1`.
- The isolated PostgreSQL 16.14 database reports the column as `integer`,
  not-null, with default `1`.
- `GameSave.version` is now `Integer`, retains `@Version`, and is initialized
  to `0`, preserving the entity's prior initial-version behavior.
- `game_sessions.version` is also `INTEGER`, but `GameSession.version` remains
  `Long`.
- `PlayerReward` declares a `Long @Version` field, but V1–V6 do not define a
  `player_rewards.version` column.

The latter two mismatches are outside the approved GameSave-only change scope.
They are expected to prevent full Hibernate schema validation against the
V1–V6 schema.

## 3. Files changed

- `backend/src/main/java/com/vietsuuky/save/entity/GameSave.java`
- `backend/src/test/java/com/vietsuuky/Phase24FoundationIntegrationTest.java`
- `PHASE_2_4_1_4_POSTGRESQL_SCHEMA_ALIGNMENT_REPORT.md`

No migration files were changed.

## 4. Was V7 necessary?

No. The existing PostgreSQL column type is correct for the intended
`GameSave` version field. Changing the Java mapping to `Integer` is the
minimal compatible fix; no database conversion or V7 migration is needed.
Existing `game_saves` rows were preserved.

## 5. Isolated PostgreSQL migration and schema results

- Database: disposable PostgreSQL 16.14 container `phase2414-postgres16`,
  exposed only on `127.0.0.1:55440`.
- Storage: `/var/lib/postgresql/data` was a container-local `tmpfs` mount;
  inspection found no Docker volumes.
- Flyway applied V1, V2, V3, V4, V5, and V6 in order, all successful.
- Flyway history contains the schema-creation pseudo-entry plus successful
  versions 1 through 6.
- Final `app.game_saves.version` definition: `INTEGER NOT NULL DEFAULT 1`.
- Targeted PostgreSQL GameSave persistence/optimistic-locking test: 1 passed,
  0 failed, 0 errors, 0 skipped. It exercised insert, read-back, update,
  version increment, and stale-version rejection. This targeted run used
  `ddl-auto=none` because other entity mappings prevent full validation; it
  therefore does not establish full Hibernate schema-validation success.
- PostgreSQL contained one account and one GameSave row after that test.

## 6. Hibernate startup and schema validation

Full application startup with Hibernate `validate` is **not confirmed**.
The Java debugger reported a started session, but the subsequent session
check found no active session; process inspection found no application JVM,
and launch diagnostics were unavailable. No claim of successful startup or
schema validation is made.

Independent schema inspection identifies two remaining validation blockers
outside the approved scope: `GameSession.version` is `Long` against an
`INTEGER` column, and `PlayerReward.version` has no corresponding column in
V1–V6. These were not changed.

## 7. GameSave persistence and optimistic locking

The focused PostgreSQL test passed all requested operations: insert, read,
update, increment the version, and reject a stale-version update while
retaining the current persisted value. The final schema still uses the
existing `INTEGER` column and its rows were not reset or truncated.

## 8. Regression tests

Command run with Microsoft OpenJDK `17.0.16`:

```text
mvn -f backend\pom.xml -Dtest=PlayerProfilePersistenceIntegrationTest,AuthApiIntegrationTest,AuthenticationFailureIntegrationTest,AuthPlayerHttpIntegrationTest,JwtServiceIntegrationTest,RegistrationPersistenceIntegrationTest,GameSessionServiceIntegrationTest,Phase24FoundationIntegrationTest test
```

Maven exit code: `0` (`BUILD SUCCESS`).

| Test class | Passed | Failed | Errors | Skipped |
|---|---:|---:|---:|---:|
| `PlayerProfilePersistenceIntegrationTest` | 1 | 0 | 0 | 0 |
| `AuthApiIntegrationTest` | 2 | 0 | 0 | 0 |
| `AuthenticationFailureIntegrationTest` | 1 | 0 | 0 | 0 |
| `AuthPlayerHttpIntegrationTest` | 3 | 0 | 0 | 0 |
| `JwtServiceIntegrationTest` | 1 | 0 | 0 | 0 |
| `RegistrationPersistenceIntegrationTest` | 1 | 0 | 0 | 0 |
| `GameSessionServiceIntegrationTest` | 2 | 0 | 0 | 0 |
| `Phase24FoundationIntegrationTest` | 3 | 0 | 0 | 0 |
| **Total** | **14** | **0** | **0** | **0** |

## 9. Remaining risks

- Full Spring Boot startup and Hibernate validation against PostgreSQL remain
  unverified. The known GameSession and PlayerReward mapping/schema
  discrepancies need separate approval and remediation.
- The successful focused PostgreSQL test used `ddl-auto=none`; it verifies
  GameSave persistence behavior, not full entity-model compatibility.
- The active development Compose database was not accessed or modified.
  No Docker volumes were deleted.

## 10. Final status

**BLOCKED** — the GameSave mapping fix, isolated migrations, focused
PostgreSQL persistence test, and 14-test Java 17 regression suite passed.
However, full application startup and Hibernate schema validation could not
be confirmed, and independently identified schema/entity mismatches remain
outside the authorized scope.
