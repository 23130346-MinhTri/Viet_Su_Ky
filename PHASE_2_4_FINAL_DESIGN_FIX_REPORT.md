# PHASE 2.4 FINAL DESIGN FIX REPORT

## 1. Issues resolved

This final design review resolved the remaining Phase 2.4 inconsistencies before implementation:

- idempotency semantics were finalized and tied to a DB-backed deduplication record
- reward uniqueness was refined to handle both one-time and repeatable rewards safely
- gameplay event evidence rules were tightened to reject client-authoritative completion state
- mission progress initialization and mission list availability were normalized to lazy evaluation
- migration compatibility was reviewed against the real repository and current Compose database
- final enum values were reconciled to the accepted design set

## 2. Final idempotency rules

Completion requests must include `Idempotency-Key`.

Behavior:
- same key + same request => return stored result
- same key + different request => HTTP `409 Conflict`
- different key + already completed session => HTTP `409 Conflict`
- concurrent requests for the same session => only one successful transaction; others fail
- validation failure => no success state; the failure may be stored as `REJECTED` or `CONFLICT` in `idempotency_records` but must not create reward rows or `COMPLETED` progress
- retryable failures => retry using the same idempotency key and same request hash; never issue a second success after rollback

Recommended persistence model:
- `idempotency_records` with unique `(operation_name, idempotency_key)`
- additional unique `(player_id, session_id, operation_name)` where one completion attempt per session is allowed
- `request_hash` stores the canonical hash of request content for same-key/different-body detection
- `response_json` stores the canonical response payload for replayed requests
- `status` values: `PENDING`, `SUCCESS`, `REJECTED`, `CONFLICT`

Transaction flow:
1. BEGIN transaction
2. lock session row (or use optimistic lock on version field)
3. validate session ownership and mission status
4. verify no prior successful completion record exists for this session or idempotency key
5. validate mission-specific evidence against trusted backend data
6. calculate final score and reward
7. insert/update `idempotency_records`
8. update `game_sessions`, `mission_progress`, and `player_progress`
9. insert reward row if allowed by DB uniqueness constraints
10. COMMIT

If any validation or DB failure occurs before COMMIT, the transaction rolls back and no success state is created.

## 3. Final reward uniqueness design

Final accepted unique model:
- one-time reward row: unique `(player_id, mission_id, reward_source, reward_type)`
- repeatable reward row: unique `(player_id, reward_source, source_reference, reward_type)`
- session-scoped one-time reward: unique `(player_id, session_id, reward_source, reward_type)`

Recommended columns on `player_rewards`:
- `player_id`
- `mission_id`
- `session_id` (nullable, for session-linked grants)
- `reward_source` (`MISSION_COMPLETION`, `ACHIEVEMENT`, `REPLAY`, `EVENT`, `OPTIONAL_MISSION`)
- `source_reference` (achievement code / replay id / event id / optional mission key)
- `reward_type` (`POINTS`, `BADGE`, `ITEM`, `UNLOCK`, `CURRENCY`)
- `reward_points`
- `reward_code`
- `status`
- `claimed_at`

Reward categories:
- one-time mission completion rewards: unique by player + mission + reward source + reward type
- replay rewards: allow same mission but differentiate by `source_reference` and/or specific session context
- achievement rewards: unique by player + achievement code + source type
- event rewards: unique by player + event id or event stream reference + reward type

This avoids accidental duplicate suppression for legitimate repeatable rewards while still preventing duplicate issuance.

## 4. Event evidence validation

Client-submitted fields are never trusted as authoritative.

Forbidden client fields:
- `isCorrect`
- `finalScore`
- `rewardPoints`
- `completedAt`
- any completion flag or mission status override

Required backend validation rules:
- validate submitted answer against the authoritative question content or answer key stored server-side
- validate checkpoint progression against backend mission progression state
- validate event ordering and business flow before accepting the event as evidence
- use `metadata` only for UI context and diagnosis; never treat it as authoritative mission state

Validation examples:
- `ANSWER_SUBMITTED`: backend checks correct answer against canonical question data and server-side attempt rules
- `CHECKPOINT_REACHED`: backend checks checkpoint index and required mission progression order
- `PLAYER_MOVEMENT`: only summarized or non-authoritative telemetry may be collected, not used for completion
- `SCENE_TICK`: never used as completion evidence
- `GAME_RENDER_EVENT`: ignored for progression and rewards

The accepted evidence submission example:

```json
{
  "evidence": {
    "questionId": "Q7",
    "selectedOption": "B",
    "attemptCount": 1,
    "answerToken": "a1b2c3"
  },
  "metadata": {
    "source": "phaser-scene",
    "checkpoint": 3
  }
}
```

The response must reflect server-accepted state only, not client assertions.

## 5. Mission progress lifecycle

Mission list behavior:
- `GET /missions` calculates availability without writing to the database for every mission row
- mission availability is resolved from static mission + prerequisite + current `mission_progress` row if present
- missing progress row must not imply completion or success

Session creation behavior:
- `POST /game-sessions` validates mission availability before creating a session
- if allowed, it lazily creates or updates `mission_progress` to `IN_PROGRESS`
- locked missions cannot start

Completion behavior:
- completion updates `game_session`, `mission_progress`, and `player_progress` atomically
- reward is inserted only once under DB uniqueness constraints
- `mission_progress` is `COMPLETED` only after backend validation passes

## 6. Migration compatibility strategy

Repository and database verification:
- actual repository contains existing Phase 2.4 entity files and V3 migration
- current Compose database includes `app.flyway_schema_history`
- V3 is already applied in the running database
- V3 must remain immutable

Final migration strategy:
- do not modify existing V3 migration files
- for future enum or schema alignment, create a new versioned migration such as `V4__phase_2_4_final_design_alignment.sql` when implementation begins
- keep legacy enum literals temporarily compatible with data and existing code until implementation migrates to the final accepted values
- plan an implementation-specific compatibility step to convert or normalize existing values before applying stricter constraints

Important note:
- No migration was executed in this design review.
- If current database verification is not possible in a future environment, the correct status is `NOT VERIFIED` instead of a claim.

## 7. Remaining risks

- future mission types may demand more explicit backend config than the MVP typed model supports
- reward replay semantics may still need product-specific policy decisions for event rewards and replay grants
- high-frequency gameplay events may create noisy telemetry if not summarized
- incorrect DB-level lock or versioning can still create timing errors under concurrent completion attempts

## 8. Recommended implementation sequence

1. review and lock final mission type + status enums in code and docs
2. add backend validators for mission-type evidence checking
3. implement idempotency records and DB uniqueness constraints
4. implement reward issuance transaction with one-time and repeatable rules
5. implement mission progress lazy initialization and availability checks
6. implement session creation and completion with transaction boundaries
7. implement save versioning and ownership checks
8. validate with unit, integration, and Docker Compose E2E tests

## Final status

READY FOR IMPLEMENTATION

This is the final design-only fix report. Implementation must not begin until explicit approval is received.
