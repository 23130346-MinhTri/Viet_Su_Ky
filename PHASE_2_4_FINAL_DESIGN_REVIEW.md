# PHASE 2.4 FINAL DESIGN REVIEW

## 1. Inconsistencies resolved

This review resolved the final Phase 2.4 design conflicts before any implementation begins.

Resolved items:
- mission enum set normalized to `QUIZ`, `EXPLORATION`, `STRATEGY`, `STORY`, `SIMULATION`
- game session lifecycle normalized to `ACTIVE`, `COMPLETED`, `ABANDONED`, `EXPIRED`
- mission progress normalized to `AVAILABLE`, `LOCKED`, `IN_PROGRESS`, `COMPLETED`
- completion payload no longer accepts client-authoritative reward/score/completion fields
- idempotency semantics explicitly defined for duplicate and concurrent completion requests
- reward model normalized to include distinct `reward_source` and `reward_type` fields
- mission progress initialization clarified to be lazy and prerequisite-based
- save API clarified as update-only unless a create endpoint is explicitly added
- review verifies the current repository already includes Phase 2.4 mission/session/progress files and a V3 migration

## 2. Final enum definitions

GameSessionStatus
- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

MissionProgressStatus
- `AVAILABLE`
- `LOCKED`
- `IN_PROGRESS`
- `COMPLETED`

MissionType
- `QUIZ`
- `EXPLORATION`
- `STRATEGY`
- `STORY`
- `SIMULATION`

Note: actual repository code still contains older enum literals such as `QUEST` and `CHALLENGE` in existing Phase 2.4 files; this review does not alter those files and instead records the final accepted design for implementation. The repository remains unchanged.

## 3. Final API completion contract

Final completion semantics:

- Endpoint: `POST /api/v1/game-sessions/{sessionId}/complete`
- Authentication: required
- Header: `Idempotency-Key` required
- Request body: mission-specific evidence only

Example request:

```json
{
  "evidence": {
    "questionId": "Q7",
    "selectedOption": "B",
    "isCorrect": true,
    "attemptCount": 1
  },
  "metadata": {
    "source": "phaser-scene",
    "checkpoint": 3
  }
}
```

Client-submitted fields explicitly forbidden:
- `finalScore`
- `rewardPoints`
- `isCompleted`
- `missionStatus`
- `completedAt`

Required backend validation:
- verify session ownership by authenticated principal
- verify session status is `ACTIVE`
- validate mission availability and prerequisites
- load mission rules from server-side configuration
- validate evidence against mission-specific rules
- calculate final score, success state, reward amount, and unlock conditions
- persist result atomically

HTTP behavior:
- same key + same request => return stored response
- same key + different request => `409 Conflict`
- different key + already completed session => `409 Conflict`
- concurrent completion => only one successful DB transaction; others fail

## 4. Reward and idempotency strategy

Reward ledger:
- `player_rewards` is the source of truth for reward issuance
- reward issuance requires both DB uniqueness and idempotency validation
- unique key: `(player_id, mission_id, reward_source, reward_type)`

Reward source examples:
- `MISSION_COMPLETION`
- `ACHIEVEMENT`
- `REPLAY`
- `EVENT`
- `OPTIONAL_MISSION`

Example reward type values:
- `POINTS`
- `BADGE`
- `ITEM`
- `UNLOCK`
- `CURRENCY`

Reward behavior:
- first completion reward: one-time
- achievement reward: one-time by mission + reward source + type
- replay reward: repeatable only if distinct source or type is used
- event reward: may be repeatable by source if business rules allow it
- optional mission reward: one-time by default unless explicitly modeled as repeatable

Idempotency persistence:
- `idempotency_records` table recommended with unique `(operation_name, idempotency_key)`
- also maintain unique `(player_id, session_id, operation_name)` where business rules permit only one completion attempt per session
- request hash stored to detect same-key different-body conflict
- response payload persisted for replayed same-key returns

Transaction boundary:
1. lock session/mission row or use optimistic versioning
2. validate user evidence and mission rules
3. compute server score and rewards
4. insert or confirm idempotency record
5. write completion and progress updates
6. insert reward only if unique constraint allows it
7. recalculate `player_progress`
8. commit transaction

## 5. Gameplay event validation strategy

High-level rule: gameplay events are evidence, not authority.

Event handling rules:
- `ANSWER_SUBMITTED`: persisted, validated, may affect completion
- `CHECKPOINT_REACHED`: persisted if game rules require checkpoint verification
- `PLAYER_MOVEMENT`: do not persist at high frequency; summarize or ignore unless required
- `SCENE_TICK`: never authoritative; do not persist as mission evidence
- `GAME_RENDER_EVENT`: never persisted as job-critical mission state

General event policy:
- only validated events update `last_event_at`
- high-frequency low-value events should be summarized or dropped
- payload size capped to a conservative maximum (recommended 8-16 KB depending on mission type)
- duplicate event handling should use event-type specific deduplication if event replay is possible
- no event may set mission completion or reward state directly; only backend completion rules may do so

## 6. Migration and entity verification results

Repository inspection result: VERIFIED

Existing Phase 2.4 entity files in repository:
- `backend/src/main/java/com/vietsuuky/mission/entity/Mission.java`
- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/java/com/vietsuuky/progress/entity/MissionProgress.java`
- `backend/src/main/java/com/vietsuuky/progress/entity/PlayerProgress.java`
- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java`
- `backend/src/main/java/com/vietsuuky/save/entity/GameSave.java`

Existing V3 migration in repository:
- `backend/src/main/resources/db/migration/V3__game_session_and_progress.sql`

Specific verification via running Compose database:
- `docker compose ps` showed `history-database-1` is running and healthy
- `psql` query on the database showed `app` schema exists and includes `flyway_schema_history`
- `app.flyway_schema_history` contains:
  - rank 0: `<< Flyway Schema Creation >>`
  - rank 1: `create app schema`
  - rank 2: `create account and player profile`
  - rank 3: `game session and progress`

Conclusion:
- the Phase 2.4 migration file was already present in the repository before this review
- V3 is recorded in Flyway history
- V3 has been applied to the current Compose database
- no modification or reset was performed during review

## 7. Remaining risks

- future mission types may require a stricter server-side config schema if gameplay logic expands beyond typed fields
- race conditions remain possible if the transaction lock or uniqueness guard is not properly implemented
- save payload growth may exceed MVP assumptions if full-game state becomes large
- UI/Phaser can still produce malformed evidence; backend validation must remain the ultimate gate

## 8. Final confirmation

This review confirms:
- all major Phase 2.4 design inconsistencies are resolved
- the final design respects authenticated principal ownership, server-side rules, and DB-enforced uniqueness
- no production code was modified
- no database migration was created or executed
- no Docker volume or existing database state was deleted or reset
- Phase 2.4 implementation has not begun and remains pending explicit approval
