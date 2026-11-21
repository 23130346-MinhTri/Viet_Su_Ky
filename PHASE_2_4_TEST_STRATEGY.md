# PHASE 2.4 TEST STRATEGY

## Testing objective

This strategy validates the final accepted Phase 2.4 design: mission availability, session lifecycle, idempotent completion, reward integrity, progress persistence, and safe React/Phaser integration. The central security boundary is that the backend decides completion, score, unlock state, and rewards; the frontend and Phaser provide evidence only.

## Final accepted definitions

MissionType:
- `QUIZ`
- `EXPLORATION`
- `STRATEGY`
- `STORY`
- `SIMULATION`

MissionProgressStatus:
- `AVAILABLE`
- `LOCKED`
- `IN_PROGRESS`
- `COMPLETED`

GameSessionStatus:
- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

## Test layers

### Unit tests

Cover logic that is deterministic and local:
- mission availability resolution
- prerequisite evaluation
- status transition validation
- reward source/type uniqueness logic
- event validation helper logic
- save payload size and JSON validation
- mission-specific score evaluation function per mission type

### Integration tests

Cover DB-backed behavior:
- session creation and ownership enforcement
- mission progress initialization
- duplicate completion handling through idempotency records
- reward uniqueness under concurrent requests
- progress recalculation after completion
- save ownership and optimistic locking
- mission completion transaction rollback on validation failure

### E2E tests

Cover browser + Docker Compose behavior:
- login and mission list rendering
- start mission flow
- Phaser session bootstrap
- completion result and reward summary rendering
- refresh/login flow after progress update

## Test cases

### 1. Mission availability

- new player gets `LOCKED` or `AVAILABLE` depending on prerequisites
- a locked mission cannot start
- an archived mission is not returned as available
- newly added mission with no prerequisite becomes `AVAILABLE` for existing players
- newly added mission with prerequisite resolves to `LOCKED` until prerequisite is `COMPLETED`

Type: unit + integration

### 2. Session ownership and lifecycle

- Player A cannot access Player B session
- Player A cannot complete or abandon Player B session
- `ACTIVE -> COMPLETED` allowed only after server validation
- `ACTIVE -> ABANDONED` allowed only while active
- `ACTIVE -> EXPIRED` after timeout threshold
- terminal states reject further transition attempts

Type: integration + API tests

### 3. Idempotent completion

- same `Idempotency-Key` + same body returns the same stored response
- same `Idempotency-Key` + different body returns `409 Conflict`
- different key + already completed session returns `409 Conflict`
- validation failure is recorded as rejected/failed but does not create a completed progress row or reward
- retryable system failures can be retried with the same key without creating duplicate success state
- no second reward row is inserted after replay

Type: integration + API tests

### 4. Concurrent completion

- two simultaneous completion requests for one session do not create duplicate rewards
- only one transaction succeeds and updates mission/session/progress state
- subsequent request fails with conflict or session already completed

Type: integration, concurrency test

### 5. Reward integrity

- reward is issued only after backend validation of mission evidence
- `player_rewards` unique constraint prevents duplicate reward rows for one-time grants
- repeatable rewards differ by `reward_source` + `source_reference` + `reward_type` so legitimate replay rewards are not blocked
- reward source and type are distinct for first-completion, replay, achievement, event, optional mission rewards
- reward amount is calculated on server and never trusted from client
- event-based reward issuance uses `source_reference` to route a single event grant uniquely and safely

Type: unit + integration

### 6. Progress persistence

- `mission_progress` row initializes lazily on first start or mission access when required
- a missing progress row is never treated as a completed mission
- `progress_value` reaches 100 on completion
- `completed_at` is set on success
- `player_progress.total_missions_completed` matches the number of completed missions
- `player_progress.total_points` reflects valid rewards only

Type: integration

### 7. Save validation and ownership

- save belongs to authenticated player only
- save name uniqueness applies per player
- invalid JSON is rejected
- oversized payload is rejected
- version mismatch is rejected with conflict

Type: integration + API tests

### 8. API authentication and authorization

- unauthenticated requests fail with `401`
- authenticated but unauthorized requests fail with `403`
- no endpoint accepts client-owned `playerId` for authorization

Type: API integration tests

### 9. React / Phaser integration

- React displays mission list from backend response
- Phaser receives session context without direct DB or progression writes
- React handles loading, empty, error, and success states
- mission result UI reflects backend verdict and reward acknowledgment

Type: frontend integration + E2E

### 10. Docker Compose browser flow

- register/login succeeds
- mission list loads
- session starts successfully
- gameplay evidence is sent to backend and accepted
- completion triggers mission progress update, reward summary, and persisted state
- refreshed page shows updated progress and rewards

Type: E2E

## Suggested test matrix

| Scenario | Unit | Integration | E2E |
|---|---:|---:|---:|
| Mission availability | Yes | Yes | No |
| Session ownership | No | Yes | Yes |
| Invalid transitions | Yes | Yes | No |
| Idempotent completion | No | Yes | Yes |
| Concurrent completion | No | Yes | No |
| Reward integrity | Yes | Yes | Yes |
| Progress persistence | No | Yes | Yes |
| Save ownership | No | Yes | Yes |
| API auth | No | Yes | Yes |
| React/Phaser bridge | Yes | Yes | Yes |
| Browser flow | No | No | Yes |

## Example backend test cases

1. `createSession_shouldFailWhenMissionIsLocked`
2. `createSession_shouldRejectSessionForOtherPlayer`
3. `completeSession_shouldRejectIfNotActive`
4. `completeSession_shouldUseServerSideValidation`
5. `completeSession_shouldAwardRewardOnceWithSameIdempotencyKey`
6. `completeSession_shouldRejectDifferentIdempotencyKeyAfterCompletion`
7. `concurrentCompleteSession_shouldPersistSingleReward`
8. `validationFailure_shouldNotCreateProgressOrReward`
9. `retryAfterSystemFailure_shouldUseSameIdempotencyKeyWithoutDuplicateSuccess`
10. `saveUpdate_shouldRejectVersionMismatch`
11. `getSave_shouldRejectOtherPlayersSave`

## Example frontend test cases

1. mission list renders backend data and states
2. mission detail shows availability and prerequisite status
3. Phaser receives active session context
4. error state renders if backend rejects mission start
5. completion response updates result and reward summary

## Release gate

Phase 2.4 is ready for implementation only when:
- mission availability and locking logic pass integration tests
- idempotency and reward uniqueness pass
- concurrent completion race condition is resolved and tested
- save ownership/versioning is proven
- E2E browser flow passes in Docker Compose

## Status

This is a design-only validation strategy. No production code or migration is executed during this review.
