# PHASE 2.4 API CONTRACT

## Design goals

- server-authoritative evaluation for all mission completion and rewards
- identity resolved from authenticated principal only
- idempotent completion flow with DB-backed deduplication
- minimal contract aligned with repository conventions
- no client-authoritative score/reward fields accepted

## Shared conventions

- Base path: `/api/v1`
- Authentication: required for all player-scoped endpoints
- Authorization: the current principal may only access their own player profile, session, progress, rewards, and saves
- Success envelope: `{ "success": true, "data": { ... } }`
- Error envelope: `{ "success": false, "message": "...", "errorCode": "...", "details": [...] }`
- Completion idempotency: `Idempotency-Key` header required on mission completion and any reward issuance endpoint
- Client-submitted fields not trusted: `finalScore`, `rewardPoints`, `isCompleted`, `missionStatus`, `completedAt`

## Final enum contract

MissionType values:
- `QUIZ`
- `EXPLORATION`
- `STRATEGY`
- `STORY`
- `SIMULATION`

MissionProgressStatus values:
- `AVAILABLE`
- `LOCKED`
- `IN_PROGRESS`
- `COMPLETED`

GameSessionStatus values:
- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

## Mission endpoints

### GET `/api/v1/missions`

Authentication: required.

Purpose: list active missions and their availability for the current player.

Response example:

```json
{
  "success": true,
  "data": [
    {
      "id": "8ee592eb-2d7d-4f02-a53a-9405585f1504",
      "code": "M01_INTRO",
      "title": "Introduction",
      "description": "Learn the founding context of the kingdom.",
      "chapter": "CHAPTER_1",
      "missionOrder": 1,
      "missionType": "QUIZ",
      "unlockCondition": "FIRST_LOGIN",
      "prerequisiteMissionId": null,
      "rewardPoints": 25,
      "rewardCode": "INTRO_REWARD",
      "status": "ACTIVE",
      "available": true,
      "progressStatus": "AVAILABLE"
    }
  ]
}
```

Status codes:
- `200 OK`
- `401 Unauthorized`
- `403 Forbidden`

### GET `/api/v1/missions/{missionId}`

Authentication: required.

Purpose: fetch a single mission definition.

### GET `/api/v1/missions/{missionId}/availability`

Authentication: required.

Purpose: resolve whether the current player can start the mission.

Response example:

```json
{
  "success": true,
  "data": {
    "missionId": "8ee592eb-2d7d-4f02-a53a-9405585f1504",
    "status": "AVAILABLE",
    "available": true,
    "reason": "Prerequisite completed"
  }
}
```

Error codes:
- `MISSION_NOT_FOUND`
- `MISSION_LOCKED`
- `MISSION_INACTIVE`

## Game session endpoints

### POST `/api/v1/game-sessions`

Authentication: required.

Request JSON:

```json
{
  "missionId": "8ee592eb-2d7d-4f02-a53a-9405585f1504"
}
```

Validation:
- `missionId` must be a valid UUID
- mission must exist and be active
- current principal must be authorized for that player
- no duplicate active session per mission if business rules enforce that model

Response example:

```json
{
  "success": true,
  "data": {
    "id": "f6f17f7a-7b93-43d1-9bbb-13ec44fa3930",
    "missionId": "8ee592eb-2d7d-4f02-a53a-9405585f1504",
    "missionCode": "M01_INTRO",
    "missionTitle": "Introduction",
    "status": "ACTIVE",
    "startedAt": "2026-11-17T08:00:00Z",
    "endedAt": null,
    "eventCount": 0,
    "finalScore": 0,
    "metadata": null
  }
}
```

Status codes:
- `201 Created`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `409 Conflict`

### GET `/api/v1/game-sessions/{sessionId}`

Authentication: required.

Purpose: fetch a session owned by the current player.

### POST `/api/v1/game-sessions/{sessionId}/events`

Authentication: required.

Request JSON:

```json
{
  "eventType": "ANSWER_SUBMITTED",
  "payload": {
    "questionId": "Q7",
    "selectedOption": "B",
    "isCorrect": true,
    "timeSpentMs": 5400
  }
}
```

Validation rules:
- session must be `ACTIVE`
- event type must be allowed for the mission type
- payload must not exceed a configured size cap
- event must be server-validated before effects are persisted
- event payload cannot override mission completion or reward state
- `last_event_at` updates only when the event is validated and persisted

Status codes:
- `200 OK`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `409 Conflict` for invalid lifecycle transition

### POST `/api/v1/game-sessions/{sessionId}/complete`

Authentication: required.

Headers:
- `Idempotency-Key: <unique-client-generated-key>`

Request JSON:

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

Not allowed from client:
- `finalScore`
- `rewardPoints`
- `isCorrect`
- `isCompleted`
- `missionStatus`
- `completedAt`

Validation rules:
- session owner matches current principal
- session status must be `ACTIVE`
- mission must be valid and available for the player
- server loads trusted mission rules from backend configuration
- server validates submitted evidence against backend question/checkpoint data
- server computes final score, success/failure, and reward amount
- request is idempotent by `(player_id, operation_name, idempotency_key)`
- `isCorrect` or equivalent answer flags are never trusted unless re-validated by the server against authoritative question data
- `metadata` is client-facing diagnostic context only and never treated as authoritative game state

Idempotency semantics:
- same key + same request => return stored response
- same key + different request => `409 Conflict`
- different key + already completed session => `409 Conflict`
- concurrent completion => only one transaction succeeds; others reject with `SESSION_ALREADY_COMPLETED` or idempotency conflict
- validation failure is not a successful completion; the request can be persisted in `idempotency_records` as `REJECTED` or `CONFLICT`, but it must never create a reward or set mission progress to `COMPLETED`
- retryable system failure (database timeout, network interruption, lock timeout) must be retried with the same idempotency key; the backend must not generate duplicate success state or duplicate rewards when the first transaction is rolled back

Success response example:

```json
{
  "success": true,
  "data": {
    "sessionId": "f6f17f7a-7b93-43d1-9bbb-13ec44fa3930",
    "status": "COMPLETED",
    "finalScore": 820,
    "rewardPoints": 25,
    "rewardCode": "INTRO_REWARD",
    "missionProgressStatus": "COMPLETED"
  }
}
```

Status codes:
- `200 OK`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `409 Conflict`
- `422 Unprocessable Entity`

### POST `/api/v1/game-sessions/{sessionId}/abandon`

Authentication: required.

Purpose: end a session without reward.

Validation:
- session must belong to current principal
- session must be `ACTIVE`
- result becomes `ABANDONED`

## Player progress endpoints

### GET `/api/v1/players/me/progress`

Authentication: required.

Purpose: aggregated and per-mission progress for current player.

### GET `/api/v1/players/me/progress/missions`

Authentication: required.

Purpose: list all mission progress rows for current player.

### GET `/api/v1/players/me/rewards`

Authentication: required.

Purpose: reward ledger for current player.

## Save endpoints

### POST `/api/v1/players/me/game-saves`

Authentication: required.

Purpose: create a new save for current player.

Request example:

```json
{
  "saveName": "chapter-1-manual-save",
  "saveData": "{\"checkpoint\": 3, \"inventory\": [\"sword\"]}",
  "missionId": "8ee592eb-2d7d-4f02-a53a-9405585f1504",
  "sessionId": "f6f17f7a-7b93-43d1-9bbb-13ec44fa3930"
}
```

### GET `/api/v1/players/me/game-saves`

### GET `/api/v1/players/me/game-saves/{saveId}`

### PUT `/api/v1/players/me/game-saves/{saveId}`

Authentication: required.

Purpose: update existing save only.

Validation:
- save belongs to authenticated player
- `saveName` is unique per player
- `saveData` is valid JSON and under size cap
- `version` must match or return `409 Conflict`
- `missionId` and `sessionId` must belong to the same player if provided

## Error catalog

- `AUTH_REQUIRED`
- `FORBIDDEN_RESOURCE`
- `MISSION_NOT_FOUND`
- `MISSION_INACTIVE`
- `MISSION_LOCKED`
- `SESSION_NOT_FOUND`
- `SESSION_ACCESS_DENIED`
- `SESSION_NOT_ACTIVE`
- `SESSION_ALREADY_COMPLETED`
- `SESSION_ALREADY_ABANDONED`
- `INVALID_SESSION_STATE`
- `INVALID_IDEMPOTENCY_KEY`
- `IDEMPOTENCY_KEY_CONFLICT`
- `REWARD_ALREADY_ISSUED`
- `REWARD_VALIDATION_FAILED`
- `SAVE_TOO_LARGE`
- `SAVE_INVALID_JSON`
- `SAVE_NAME_CONFLICT`
- `SAVE_VERSION_CONFLICT`
- `PLAYER_PROFILE_NOT_FOUND`
- `VALIDATION_ERROR`

## Security rules

1. Never trust `accountId`, `playerId`, or any other client-supplied identity as an authorization boundary.
2. Never trust `score`, `reward`, `unlock`, `missionStatus`, or completion flags from the browser.
3. Validate mission-specific evidence on the backend before determining completion.
4. All reward insertion and completion transactions must run under single DB transaction boundaries.
5. Reward issuance is safe only with DB uniqueness + idempotency record enforcement.

## Design decision

The final API contract is intentionally minimal and explicitly separates authoritative backend evaluation from the non-authoritative frontend/Phaser evidence stream.

## Status

This is a design-only contract revision. No production endpoint implementation or migration is executed in this phase.
