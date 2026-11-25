# PHASE 2.4.3 - PRE-IMPLEMENTATION AUDIT

## 1. Executive summary

**Final audit recommendation: BLOCKED**

Phase 2.4.3 cannot be implemented safely from the current repository alone.

The repository has a session lifecycle, event endpoint, completion endpoint, reward ledger, idempotency entity/table, mission progress, player progress, and game save foundation. It does not contain the canonical gameplay evidence needed to make completion authoritative:

- no question entity or repository
- no answer/correct-answer entity or repository
- no checkpoint entity or repository
- no mission-specific completion rule implementation
- no event evidence validation
- no event ordering or replay prevention
- no completion idempotency header or service implementation
- no canonical server-side score calculation
- no completion transaction test coverage

The most significant blocker is that `CompleteGameSessionRequest.finalScore` is accepted by the service and persisted directly. This conflicts with the approved design, which requires mission evidence to be validated server-side and final score/reward to be calculated by the backend.

## 2. Documentation inspected

The following project documents were inspected:

- `docs/README.md`
- `viet-su-ky-ai-docs/AI_WORKFLOW.md`
- `viet-su-ky-ai-docs/PROJECT_CONTEXT.md`
- `viet-su-ky-ai-docs/PRODUCT_GDD.md`
- `viet-su-ky-ai-docs/TECHNICAL_DESIGN.md`
- `viet-su-ky-ai-docs/SRS.md`
- `viet-su-ky-ai-docs/ARCHITECTURE.md`
- `PHASE_2_4_GAMEPLAY_LIFECYCLE.md`
- `PHASE_2_4_FINAL_DESIGN_REVIEW.md`
- `PHASE_2_4_DATABASE_DESIGN.md`
- `PHASE_2_4_API_CONTRACT.md`
- `PHASE_2_4_2_PRE_IMPLEMENTATION_AUDIT.md`
- `PHASE_2_4_2_FINAL_REPORT.md`
- `PHASE_2_4_2_1_SESSION_EXPIRATION_AUDIT.md`
- `PHASE_2_4_2_2_PRE_IMPLEMENTATION_AUDIT.md`
- `PHASE_2_4_2_2_IMPLEMENTATION_PLAN.md`
- `PHASE_2_4_2_2_FINAL_REPORT.md`
- `PHASE_2_4_2_3_FINAL_VERIFICATION_REPORT.md`

Verified design decisions include:

- backend is authoritative
- client may send intent/evidence, not authoritative score/reward/state
- gameplay events must be validated before persisted or used
- completion must use server evidence validation and atomic state updates
- completion must be idempotent
- rewards must be database-protected
- terminal sessions cannot return to ACTIVE

## 3. Actual repository implementation inspected

### Session pipeline

- `backend/src/main/java/com/vietsuuky/session/controller/GameSessionController.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`
- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`
- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionEventRequest.java`
- `backend/src/main/java/com/vietsuuky/session/dto/CompleteGameSessionRequest.java`

### Mission and progress pipeline

- `backend/src/main/java/com/vietsuuky/mission/entity/Mission.java`
- `backend/src/main/java/com/vietsuuky/mission/service/MissionService.java`
- `backend/src/main/java/com/vietsuuky/progress/entity/MissionProgress.java`
- `backend/src/main/java/com/vietsuuky/progress/entity/PlayerProgress.java`
- `backend/src/main/java/com/vietsuuky/progress/service/MissionProgressService.java`
- `backend/src/main/java/com/vietsuuky/progress/service/PlayerProgressService.java`
- `backend/src/main/java/com/vietsuuky/progress/repository/MissionProgressRepository.java`
- `backend/src/main/java/com/vietsuuky/progress/repository/PlayerProgressRepository.java`

### Reward and idempotency pipeline

- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java`
- `backend/src/main/java/com/vietsuuky/reward/service/PlayerRewardService.java`
- `backend/src/main/java/com/vietsuuky/reward/repository/PlayerRewardRepository.java`
- `backend/src/main/java/com/vietsuuky/session/entity/IdempotencyRecord.java`
- `backend/src/main/java/com/vietsuuky/session/repository/IdempotencyRecordRepository.java`

### GameSave pipeline

- `backend/src/main/java/com/vietsuuky/save/entity/GameSave.java`
- `backend/src/main/java/com/vietsuuky/save/service/GameSaveService.java`
- `backend/src/main/java/com/vietsuuky/save/controller/GameSaveController.java`
- `backend/src/main/java/com/vietsuuky/save/dto/GameSaveRequest.java`

### Database

Inspected migrations V1–V9.

## 4. Current architecture and data flow

Authenticated player ID is resolved by the controller from `@AuthenticationPrincipal`, not by request player fields.

Current event flow:

1. Controller accepts `eventType` and opaque `payload`.
2. Service verifies session ownership and row lock.
3. Service verifies ACTIVE status and expiration.
4. Service increments event count.
5. Service stores metadata containing event type and payload text.
6. Service increments mission progress value by at least 10.

Current completion flow:

1. Controller accepts `CompleteGameSessionRequest`.
2. Service locks the session and verifies ownership/expiration/status.
3. Service uses request `finalScore`, or zero if null.
4. Service marks session COMPLETED.
5. Service marks mission progress COMPLETED.
6. Service checks/inserts reward.
7. Service recalculates player progress.

No request evidence is validated against canonical question, answer, or checkpoint data.

## 5. Gameplay event validation findings

### Finding 5.1 - Event type is unrestricted

Evidence:

- `GameSessionEventRequest.java` only requires `eventType` to be nonblank.
- `GameSessionService.recordEvent` does not switch or allowlist event types.
- It accepts any nonblank event string.

Actual behavior:

- arbitrary event types are accepted for an eligible ACTIVE session
- event count and mission progress are changed
- metadata is updated with no canonical interpretation

Expected behavior:

- event type must be recognized for the mission/session
- mission-specific evidence must be validated server-side
- invalid events must not change progress

Severity: HIGH

### Finding 5.2 - Payload is opaque

Evidence:

- `GameSessionEventRequest.payload` is a single unstructured string.
- `recordEvent` interpolates the payload into session metadata without parsing or validating it.

Actual behavior:

- payload cannot prove answer correctness or checkpoint progression
- arbitrary payload text can increase mission progress to at least 10

Expected behavior:

- use typed server DTO/evidence model
- validate required fields and mission-specific semantics
- persist only necessary high-value evidence

Severity: HIGH

### Finding 5.3 - Replay and ordering are not controlled

No repository or table for gameplay event IDs, sequences, hashes, or event deduplication exists.

Actual behavior:

- the same event can be replayed repeatedly
- event count increments for every replay
- no ordering constraint exists
- no duplicate-evidence check exists

Expected behavior:

- reject replayed or out-of-order evidence where approved mission rules require it
- persist and deduplicate high-value evidence
- do not let duplicate event evidence inflate progress

Severity: HIGH

### Finding 5.4 - Ownership and expiration are protected

Evidence:

- `GameSessionController` derives player from principal.
- `GameSessionRepository.findForUpdateByIdAndPlayerId` locks owner/session row.
- `recordEvent` rejects expired and terminal sessions.

Actual behavior:

- ownership is protected.
- expiration and row locking are integrated.

Status: PARTIALLY SATISFACTORY

The safety of event validation is low because no canonical evidence exists, but lifecycle/ownership protection is present.

## 6. Score calculation findings

### Finding 6.1 - Client finalScore is authoritative

Evidence:

- `CompleteGameSessionRequest.java` declares `finalScore`.
- `GameSessionController.completeSession` accepts the DTO without a header or evidence-specific validation.
- `GameSessionService.completeSession` executes `request.finalScore()`, null is coerced to zero.

Actual behavior:

- client can submit an arbitrary nonnegative integer, subject only to `Math.max(0, ...)`.
- no upper-bound check exists.
- no answer/checkpoint evidence is validated.
- no mission-specific score model exists.

Expected behavior:

- remove or ignore client finalScore
- calculate score from server-side canonical evidence
- enforce mission-specific limits and partial completion rules

Severity: CRITICAL

### Finding 6.2 - durationSeconds is unused

Evidence:

- `CompleteGameSessionRequest.durationSeconds` exists.
- `completeSession` does not read it.

Actual behavior:

- the field is currently inert
- it must not be considered safe or validated merely because it is present

Severity: MEDIUM

### Finding 6.3 - No canonical question or checkpoint data

Repository search found no:

- question entity/repository
- answer entity/repository
- correct-answer field
- checkpoint entity/repository
- mission rule entity/repository

Actual behavior:

- answer correctness cannot be recalculated
- checkpoint progression cannot be verified
- mission completion cannot be derived from canonical data

Severity: CRITICAL

## 7. Mission completion findings

### Finding 7.1 - Completion does not require validated evidence

Evidence:

- `completeSession` only checks ownership, expiration, and ACTIVE status.
- It does not require a request evidence structure.
- It does not check prerequisites or mission availability at completion time.

Actual behavior:

- an eligible ACTIVE session can be completed with a client-supplied score and metadata.
- mission availability can change after session start and is not rechecked in completion.

Severity: CRITICAL

### Finding 7.2 - Completion is transactionally grouped

Evidence:

- `completeSession` is `@Transactional(noRollbackFor = ApiException.class)`.
- it updates session, mission progress, reward, and player progress.

Verified behavior:

- the method has one transaction boundary for the service operations.
- runtime exceptions should roll back related changes.
- expiration is committed before an `SESSION_EXPIRED` rejection.

Risk:

- no idempotency record protects replay/concurrent completion semantics.
- no evidence validation protects correctness.
- player progress recalculation can be inconsistent under concurrent mission completion.

Severity: HIGH

### Finding 7.3 - State transitions are partial

The session supports ACTIVE, COMPLETED, ABANDONED, and EXPIRED.

Actual behavior:

- ACTIVE -> COMPLETED is implemented directly.
- terminal states reject ordinary return to ACTIVE.
- expiration is locked/verified.

Missing:

- completion idempotency record
- canonical evidence validation
- server score
- completion response replay

Severity: HIGH

## 8. Reward and idempotency findings

### Finding 8.1 - Idempotency table exists but is not used

Evidence:

- V4 creates `app.idempotency_records`.
- `IdempotencyRecord` and repository exist.
- unique constraints protect operation/key and player/session/operation combinations.

Actual behavior:

- completion controller does not accept `Idempotency-Key`.
- `completeSession` does not read or persist an idempotency record.
- no request hash is stored.
- no replayed response is stored.
- same-key/different-payload conflict is not implemented.

Severity: CRITICAL

### Finding 8.2 - Reward uniqueness protects some duplicates

Evidence:

- V4 adds reward unique indexes for mission reward, repeatable reward, and session reward.
- V5/V6 add repeatable source-reference policy.
- `PlayerReward` also declares database unique constraints.
- `PlayerRewardService` checks whether a mission reward exists before save.

Actual behavior:

- database constraints provide a strong fallback for repeat/reward uniqueness.
- duplicate session completion is less likely due to the session row lock and ACTIVE check.

Limitations:

- no idempotency is used to replay the same response.
- no service sets `session_id` or `source_reference` for the mission reward.
- reward uniqueness alone does not prove evidence or completion validity.

Severity: HIGH

### Finding 8.3 - Reward source is not completion-specific in service

Evidence:

- `awardMissionReward` creates `PlayerReward` with `MISSION_COMPLETE` reward source.
- it does not validate that completion succeeded from canonical evidence.

Actual behavior:

- any current `completeSession` call can issue mission reward if the session becomes COMPLETED.

Severity: CRITICAL

## 9. Player progress findings

### Finding 9.1 - Mission progress has a uniqueness constraint and row lock

Evidence:

- V3 unique `(player_id, mission_id)`.
- `MissionProgressRepository.findForUpdateByPlayerIdAndMissionId` uses `PESSIMISTIC_WRITE`.
- `markCompleted` uses the locked progress row.

Actual behavior:

- mission progress completion updates are protected from a concurrent duplicate mission-progress mutation.

Status: SATISFACTORY for mission progress row locking.

### Finding 9.2 - Player progress recalculation is not row-locked

Evidence:

- `PlayerProgressRepository` has no pessimistic/conditional lock.
- `PlayerProgressService.recalculateForPlayer` reads mission/reward state and saves `PlayerProgress`.
- `PlayerProgress.player` is one-to-one without an optimistic `@Version`.

Actual behavior:

- concurrent mission completions for the same player can read overlapping snapshots and save derived counters.
- lost updates are possible even though mission progress rows are individually locked.

Expected behavior:

- lock the player progress row or use a DB transaction/aggregate update under a player-level lock.

Severity: HIGH

### Finding 9.3 - Player progress is derived and can disagree

Evidence:

- total completed count is recalculated from mission progress.
- total points is recalculated from reward ledger.
- unlocked content is recalculated from mission availability.

Actual behavior:

- stale or failed transaction can be visible as inconsistent until recalculation runs again.
- no invariant test proves all four states stay synchronized.

Severity: MEDIUM/HIGH

## 10. GameSave dependency assessment

GameSave is already implemented for:

- list saves
- get save
- upsert by save ID/name
- player ownership
- optional mission/session linkage
- optimistic `@Version`
- payload size check

Evidence:

- `GameSaveService`
- `GameSaveController`
- `GameSave` entity
- V3 game_saves table

No endpoint currently uses save data to complete a mission or adjust rewards.

Assessment:

- GameSave is not a blocker to implementing server-authoritative events/completion.
- save data must not become authoritative evidence until a future save model explicitly defines validated checkpoint state.
- save session linkage can be used for ownership only if mission/session consistency is also checked.
- current `upsertSave` does not verify that a supplied mission belongs to the supplied session.
- save payload may contain arbitrary JSON text, but it is not currently used for completion.

Status: COMPATIBLE BUT INDEPENDENT

## 11. Security and data integrity findings

### 11.1 Client-controlled fields

Client-controlled fields that directly affect progress, score, or reward:

| Field | Location | Actual influence |
|---|---|---|
| `eventType` | GameSessionEventRequest | accepted without allowlist |
| `payload` | GameSessionEventRequest | stored and not validated |
| `finalScore` | CompleteGameSessionRequest | persisted directly |
| `metadata` | CompleteGameSessionRequest | stored as text; does not affect reward in current code |
| `durationSeconds` | CompleteGameSessionRequest | accepted but unused |

`missionStatus`, `isCompleted`, `rewardPoints`, `completedAt`, and `accountId/playerId` are not accepted in the current DTOs.

### 11.2 Ownership

Session ownership is derived from authenticated principal and protected by repository owner/session predicates.

Cross-player access is blocked for session operations.

### 11.3 Terminal lifecycle

EXPIRED sessions cannot be completed or receive events through the current service.

Terminal state is protected with row locking and status checks.

### 11.4 Transaction integrity

The completion transaction can roll back runtime failures, but it has no evidence/idempotency integrity contract.

The expiration rejection deliberately does not roll back the expiration transition.

This behavior was separately verified in Phase 2.4.2.3.

### 11.5 Reward integrity

Database reward indexes provide a fallback duplicate guard.

The completion path is not protected by an idempotency request hash or stored response.

## 12. Database and migration assessment

### Existing V1–V9

Relevant existing structures:

- missions
- game_sessions
- mission_progress
- player_progress
- player_rewards
- game_saves
- idempotency_records

Verified constraints:

- mission progress unique `(player_id, mission_id)`
- mission prerequisite FK
- game session player/mission FKs
- ACTIVE session partial unique index
- reward uniqueness indexes
- idempotency operation/key unique constraint
- idempotency player/session/operation unique constraint
- reward source-reference constraints
- session `last_activity_at` and expiration index

### Missing schema for server-authoritative completion

A future approved implementation needs at least one of:

- question/answer/checkpoint tables
- mission completion/evidence configuration
- event evidence record and deduplication
- idempotency completion request record already exists but needs service semantics

No additional migration can safely solve completion authority until canonical mission evidence is specified.

V1–V9 should remain immutable.

## 13. Existing test coverage

Existing tests include:

- `GameSessionServiceIntegrationTest`
- `GameSessionExpirationPolicyTest`
- `Phase2423PostgresConcurrencyTest`
- `Phase24FoundationIntegrationTest`
- `Phase2416PostgresIntegrationTest`
- authentication and player profile regression classes

Verified behaviors include:

- session creation
- mission progress lazy initialization
- cross-player session access
- duplicate ACTIVE session
- locked mission rejection
- session expiration boundary
- scheduled expiration
- expiration versus event/completion/abandonment concurrency
- database reward constraints
- optimistic locking foundations

## 14. Missing test coverage

Missing:

- valid/invalid event type allowlist
- payload validation
- event replay
- event ordering
- event deduplication
- event payload limit
- canonical answer validation
- canonical checkpoint validation
- client-forged finalScore
- server-score boundaries
- incomplete completion rejection
- mission availability recheck at completion
- completion Idempotency-Key presence
- same key/same request replay
- same key/different payload conflict
- concurrent completion with idempotency
- reward not issued after validation failure
- mission progress not changed after validation failure
- player progress consistency under concurrent mission completions
- repeated completion response
- HTTP completion endpoint tests
- reward/session linkage
- stale completion cannot return terminal session to ACTIVE

## 15. Implementation dependency graph

```text
Approved canonical mission evidence model
                |
                v
Gameplay event contract + allowlist
                |
                v
Server-side evidence validation
                |
                v
Server-side score calculation
                |
                v
Completion transaction
       /              \
      v                v
Mission progress    Idempotency
      \                /
       v              v
       Reward issuance
                |
                v
Player progress recalculation
```

GameSave remains independent and should not be placed in the critical path until save semantics are explicitly approved.

## 16. Recommended vertical slice

The smallest useful vertical slice should be a single mission with:

- one canonical server-side answer/checkpoint definition
- one allowed event type
- one server-calculated pass threshold
- one completion endpoint
- one mission reward
- one idempotency key

It should not start with all mission types or multi-mission concurrency.

Before implementation, the project needs approval of:

- canonical evidence schema
- mission rule storage
- server score formula
- event ordering/replay policy
- HTTP completion header and error contract

## 17. Recommended implementation order

1. Define and approve the minimal canonical mission evidence model.
2. Add server-side mission/question/checkpoint entities or configuration.
3. Add typed event validation and event deduplication.
4. Add server score calculation.
5. Replace client `finalScore` completion semantics.
6. Implement completion idempotency.
7. Make reward and session completion one transaction.
8. Lock/recalculate player progress safely.
9. Add HTTP, unit, integration, PostgreSQL, and concurrency tests.
10. Assess save compatibility only after the completion slice is stable.

No phase implementation should proceed until the canonical evidence model and score policy are approved.

## 18. Risks and unresolved questions

### Unresolved questions

- Which mission types require evidence in the MVP?
- Is quiz answer validation the first vertical slice?
- What is the canonical question/checkpoint schema?
- Are repeated identical events replay or valid repeated evidence?
- Which event types are valid for each mission?
- How are event payload limits and ordering encoded?
- What exact server score formula is approved?
- Is partial mission completion possible?
- Is `durationSeconds` retained or removed?
- When is mission progress changed during gameplay?
- How is completion idempotency response replay represented?

### Risks

- critical client score trust
- arbitrary event types/payload
- replay/out-of-order evidence inflation
- no canonical answer/checkpoint data
- no completion idempotency in service
- possible concurrent player progress lost updates
- reward issuance after insufficient evidence
- partial completion semantics drift
- save data could become incorrectly authoritative if later reused

## 19. Explicit files likely to change in the first implementation slice

Likely:

- `backend/src/main/java/com/vietsuuky/session/dto/CompleteGameSessionRequest.java`
- `backend/src/main/java/com/vietsuuky/session/controller/GameSessionController.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`
- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionEventRequest.java`
- `backend/src/main/java/com/vietsuuky/session/entity/IdempotencyRecord.java`
- `backend/src/main/java/com/vietsuuky/session/repository/IdempotencyRecordRepository.java`
- `backend/src/main/java/com/vietsuuky/progress/service/MissionProgressService.java`
- `backend/src/main/java/com/vietsuuky/progress/service/PlayerProgressService.java`
- `backend/src/main/java/com/vietsuuky/reward/service/PlayerRewardService.java`
- `backend/src/main/resources/db/migration/V10__...sql` if an approved canonical evidence schema is needed
- focused unit/integration/PostgreSQL test classes

Not in the first evidence slice unless separately approved:

- `GameSaveService`
- save payload format redesign
- frontend/Phaser
- mission content expansion
- reward policy redesign

## 20. Final audit recommendation

**BLOCKED**

Phase 2.4.3 is not ready for implementation because the repository has no canonical gameplay evidence model and accepts client-authoritative completion score.

The existing expiration, ownership, session locking, reward constraints, and idempotency persistence foundation are useful dependencies, but they do not replace server-side event validation or score calculation.
