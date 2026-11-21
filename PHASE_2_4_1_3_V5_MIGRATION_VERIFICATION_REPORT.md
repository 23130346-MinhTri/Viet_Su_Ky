# Phase 2.4.1.3 — V5 Migration Verification Report

## Final status: PASS WITH WARNINGS

V1–V5 were successfully applied and validated on a disposable PostgreSQL 16 database. The SQL audit found that V5 accepts empty and whitespace-only references, despite the entity’s non-blank policy, and adds a redundant partial unique index. V5 was left unchanged; forward migration V6 enforces non-blank references and drops only that redundant V5 index. V6 was also validated and tested on the isolated database.

## 1. V5 SQL review

Reviewed `backend/src/main/resources/db/migration/V5__phase_2_4_reward_integrity_hardening.sql`, V4, the reward entity, and reward types. The V5 CHECK is equivalent to:

```sql
reward_source NOT IN ('ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION')
OR source_reference IS NOT NULL
```

It does not trim or test the contents of `source_reference`. Thus SQL V5 requires a value, not a non-blank value. The Java `PlayerReward.validateRewardPolicy()` rejects both null and `String.isBlank()` values for those four sources, so direct SQL inserts and ORM-managed inserts have different blank-value behavior.

The only reward enum is `RewardType` (`MISSION_COMPLETE`, `EVENT_REWARD`, `DAILY_BONUS`). `reward_source` is a string constrained by SQL, not a Java enum.

## 2. V4/V5 index overlap

V4 creates:

- `uk_player_rewards_mission_reward` on `(player_id, mission_id, reward_source, reward_type)`
- `uk_player_rewards_repeatable` on `(player_id, reward_source, source_reference, reward_type)`
- `uk_player_rewards_session_reward` on `(player_id, session_id, reward_source, reward_type)`

V5 adds `uk_player_rewards_repeatable_reference` on the same key columns as V4’s `uk_player_rewards_repeatable`, filtered to repeatable sources with non-NULL references. V4’s unfiltered unique index already enforces uniqueness for every non-NULL key in that V5 predicate. The partial index is therefore redundant; both are not needed. PostgreSQL’s default unique-index NULL semantics allow multiple NULL values, but V5’s CHECK separately rejects NULL references for the four repeatable sources.

V6 drops only `uk_player_rewards_repeatable_reference`. V4 is unchanged and remains the unique constraint enforcing repeatable-reference deduplication.

## 3. Isolated PostgreSQL migration results

- Used the locally available PostgreSQL `16.14` image in a fresh `v5_verify` database.
- Container was bound only to `127.0.0.1:55439`; `/var/lib/postgresql/data` was mounted as tmpfs. No Compose service or Docker volume was used.
- Flyway created schema `app`, then applied V1, V2, V3, V4, and V5 in order. It reported: `Successfully applied 5 migrations`.
- Standalone Flyway validation reported `Successfully validated 6 migrations` (five versioned migrations plus Flyway’s schema-creation history entry); validation exited successfully.
- After identifying the V5 blank-reference gap, V6 was applied in the same disposable database; Flyway reported successful validation and application of V6. A subsequent validation reported `Successfully validated 7 migrations` (six versioned migrations plus schema creation).
- Table columns, CHECK constraints, and indexes were inspected. Post-V6, all four reward-source/type/status checks and the three V4 reward unique indexes remained; the V5 partial index was absent.
- Controlled SQL fixtures ran inside transactions and were rolled back. Final fixture counts were zero. The isolated container was stopped and removed; no volumes were deleted.
- The first Spring Boot migration attempt was blocked by the host timezone alias `Asia/Saigon`. Re-running with the verification JVM pinned to UTC allowed Flyway to connect and migrate.

## 4. Constraint behavior test matrix

| Case | V5 PostgreSQL result | V6 result / interpretation |
|---|---|---|
| `source_reference = NULL`, each of ACHIEVEMENT, REPLAY, EVENT, OPTIONAL_MISSION | Rejected by `player_rewards_required_source_reference_check` | Still rejected |
| `source_reference = NULL`, MISSION_COMPLETION | Accepted; used for one-time mission rewards | Still accepted |
| `source_reference = ''`, repeatable source | Accepted by V5 SQL | Rejected by V6 CHECK |
| `source_reference` containing only spaces | Accepted by V5 SQL | Rejected by V6 CHECK |
| `source_reference` containing only tab/newline whitespace | Not specifically exercised under V5; V5 predicate accepts any non-NULL value | Rejected by V6 CHECK |
| Valid non-blank reference | Accepted | Accepted |
| Duplicate repeatable reward with same player/source/reference/type | Rejected by V4’s broader unique index (the V5 partial index is redundant) | Rejected by V4’s index after V6 removes the partial index |
| Repeatable rewards with different references | Accepted | Accepted |
| One-time mission rewards, same player and reward type on distinct missions | Accepted | V5 SQL test confirmed this |
| Duplicate one-time reward on the same player/mission/source/type | Rejected by `uk_player_rewards_mission_reward` | Unchanged |
| Session-scoped rewards with same player/session/source/type but different missions | Duplicate rejected by `uk_player_rewards_session_reward` | Unchanged |
| Same session-scoped reward in different sessions | Accepted | Unchanged |
| Rows with `session_id = NULL` and otherwise distinct mission keys | Accepted; PostgreSQL treats NULLs as distinct for these unique indexes | Unchanged |

The V5 SQL matrix used actual inserts against PostgreSQL 16. The V6 check was separately exercised for NULL, empty, spaces, and tab/newline-only references; the V4 repeatable index was verified to reject duplicates after V6 dropped the partial index.

## 5. Flyway history

| Rank | Version | Script | Result |
|---:|---:|---|---|
| 0 | — | Flyway schema creation (`app`) | Success |
| 1 | 1 | `V1__create_app_schema.sql` | Success |
| 2 | 2 | `V2__create_account_and_player_profile.sql` | Success |
| 3 | 3 | `V3__game_session_and_progress.sql` | Success |
| 4 | 4 | `V4__phase_2_4_foundation_compatibility.sql` | Success |
| 5 | 5 | `V5__phase_2_4_reward_integrity_hardening.sql` | Success |
| 6 | 6 | `V6__phase_2_4_reward_reference_nonblank.sql` | Success |

The V1 `CREATE SCHEMA IF NOT EXISTS` emitted a harmless “schema already exists” notice because Flyway had created the configured schema. All history entries were successful.

## 6. Regression test results

Executed the same eight Phase 2.4.1.2 classes with Maven running on Java `17.0.16`:

| Test class | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| `AuthApiIntegrationTest` | 2 | 0 | 0 | 0 |
| `RegistrationPersistenceIntegrationTest` | 1 | 0 | 0 | 0 |
| `AuthPlayerHttpIntegrationTest` | 3 | 0 | 0 | 0 |
| `AuthenticationFailureIntegrationTest` | 1 | 0 | 0 | 0 |
| `JwtServiceIntegrationTest` | 1 | 0 | 0 | 0 |
| `PlayerProfilePersistenceIntegrationTest` | 1 | 0 | 0 | 0 |
| `GameSessionServiceIntegrationTest` | 2 | 0 | 0 | 0 |
| `Phase24FoundationIntegrationTest` | 2 | 0 | 0 | 0 |
| **Total** | **13** | **0** | **0** | **0** |

Maven result: `BUILD SUCCESS`, exit code 0. `PlayerProfilePersistenceIntegrationTest`, all authentication/JWT tests, and foundation tests passed. These tests use the in-memory H2 `test` profile and did not connect to Compose.

## 7. Files modified

- `backend/src/main/resources/db/migration/V6__phase_2_4_reward_reference_nonblank.sql` — forward-only check strengthening and removal of the redundant V5 partial index.
- `PHASE_2_4_1_3_V5_MIGRATION_VERIFICATION_REPORT.md` — this report.

V1, V2, V3, V4, and V5 were not modified. No active development database was accessed or changed.

## 8. Remaining risks

- A Spring Boot startup attempt reached successful Flyway migration, then Hibernate schema validation failed on `app.game_saves.version`: PostgreSQL reported `int4` while the entity mapping expected `bigint`. This is outside the V5/V6 reward change and was not modified. Flyway validation and migration were independently confirmed with the Flyway Maven plugin.
- V6 has been applied and validated only on the disposable local PostgreSQL database. It has not been applied to any development database.
- Entity validation uses Java `String.isBlank()` while V6 uses PostgreSQL’s POSIX `[[:space:]]` character class; the tested ASCII whitespace cases match, but Unicode whitespace equivalence was not exhaustively tested.

## Final status

**PASS WITH WARNINGS**
