# PHASE 2.4.3.1 - QUIZ MISSION CONTRACT & EVIDENCE DESIGN

## 1. Executive summary

**Final recommendation: READY FOR IMPLEMENTATION, subject to approval of the five canonical quiz questions and their answer options.**

This document defines the first server-authoritative Quiz Mission vertical slice. It does not define or modify historical question content.

Approved quiz rules are:

- exactly 5 canonical questions per mission attempt
- 20 points per correct answer
- maximum 100 points
- exactly one recorded answer per question
- invalid/incorrect answers are valid recorded answers
- all 5 questions must be answered before completion
- no client-provided score, correctness, completion, or reward field
- duplicate delivery is idempotent
- answering an already answered question with a different payload is rejected
- out-of-order and skipped submissions are rejected
- GameSave is not in scope

The repository currently has no canonical question or answer model. `Mission` has no quiz-question relationship, `GameSessionEventRequest` accepts an arbitrary event type and opaque payload, and `CompleteGameSessionRequest` accepts client `finalScore`.

The recommended design adds a small Quiz module and a forward-only V10 migration. It does not require a redesign of Mission or GameSession foundations.

## 2. Existing implementation findings

### 2.1 Mission

Verified:

- `backend/src/main/java/com/vietsuuky/mission/entity/Mission.java`
- Mission has code, title, chapter, order, type, prerequisite, reward configuration, and status.
- `missionType` supports QUIZ.
- No question relationship or mission completion rules are present.

Missing:

- canonical quiz question data
- question membership for missions
- question order
- correct answer data
- mission completion threshold/configuration

### 2.2 GameSession

Verified:

- `backend/src/main/java/com/vietsuuky/session/entity/GameSession.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`
- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`
- session statuses are ACTIVE, COMPLETED, ABANDONED, and EXPIRED
- session creation and ownership use pessimistic locking for mutations
- `recordEvent` and `completeSession` are transactional
- expiration is rechecked by session operations
- V9 adds `last_activity_at`

Missing:

- quiz attempt snapshot
- question order snapshot
- immutable answer evidence
- server score calculation
- completion idempotency in `completeSession`

### 2.3 Gameplay events

Verified existing behavior:

- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionEventRequest.java`
  - `eventType`: only `@NotBlank`
  - `payload`: opaque String
- `GameSessionService.recordEvent`
  - validates ownership
  - verifies ACTIVE and expiration
  - increments `event_count`
  - stores metadata
  - increments mission progress value to at least 10
  - updates mission progress `lastEventAt`

Missing behavior:

- allowed event-type allowlist
- typed answer payload
- question membership validation
- answer option validation
- question ordering
- replay deduplication
- immutable answer persistence

The generic event endpoint must not be used as the authoritative Quiz answer endpoint.

### 2.4 Completion

Verified existing behavior:

- `backend/src/main/java/com/vietsuuky/session/dto/CompleteGameSessionRequest.java`
- fields: `finalScore`, `metadata`, `durationSeconds`
- `GameSessionService.completeSession`
  - uses client `finalScore`
  - marks session COMPLETED
  - marks mission progress COMPLETED
  - awards mission reward
  - recalculates player progress

This is not server-authoritative for Quiz completion.

Missing behavior:

- removal/rejection of client score
- evidence count validation
- server score calculation
- Idempotency-Key handling
- request hash validation
- stored completion response replay

### 2.5 Reward and idempotency

Verified:

- `backend/src/main/java/com/vietsuuky/reward/entity/PlayerReward.java`
- `backend/src/main/java/com/vietsuuky/reward/service/PlayerRewardService.java`
- V4 creates `app.idempotency_records`
- V4/V5/V6 protect reward uniqueness
- V4 defines unique constraints for operation/key and player/session/operation
- DB reward indexes protect mission, repeatable, and session reward uniqueness

Missing:

- completion controller Idempotency-Key
- request hash in `completeSession`
- response replay
- same-key/different-request validation
- setting mission reward `session_id`
- answer-event idempotency granularity

Existing `IdempotencyRecord` is suitable for completion when extended to validate and replay completion responses. It is not sufficient for each Quiz answer because one answer request can exist at several positions in one session.

### 2.6 Mission and player progress

Verified:

- `MissionProgress` has unique `(player_id, mission_id)`.
- `MissionProgressRepository.findForUpdateByPlayerIdAndMissionId` uses pessimistic locking.
- `markCompleted` sets status COMPLETED, progress 100, and timestamps.
- `PlayerProgress` stores derived totals and unlocked content.
- `PlayerProgressRepository` has no pessimistic lock.

Missing:

- score/evidence-aware completion
- player progress row locking

### 2.7 GameSave

Verified:

- `GameSaveService` implements save upsert/read with ownership checks.
- `GameSave` supports mission/session references and optimistic locking.

Not used for completion evidence and excluded from this design.

## 3. Approved business rules

1. A Quiz Mission attempt contains exactly 5 canonical questions.
2. Questions are ordered 1 through 5.
3. Every question is worth 20 points.
4. Maximum score is 100.
5. Correctness is calculated only from backend canonical data.
6. Each question accepts exactly one answer in the session.
7. Incorrect answers are valid evidence and receive zero points.
8. Submission of a fourth or later question is valid only after the earlier required question has been recorded.
9. Reordering or skipping questions is rejected.
10. Duplicate delivery of the same answer request returns the original answer result.
11. A different request for an already recorded question is rejected.
12. All five evidence records are required for completion.
13. Client score, correctness, completion status, reward, and timestamps are never authoritative.
14. GameSave evidence is not used.

## 4. Question data model

### 4.1 Recommended V10 tables

The first vertical slice needs canonical Quiz question data and a session snapshot. A forward-only V10 migration is justified.

#### `app.quiz_questions`

Purpose: immutable canonical question versions.

Recommended columns:

- `id UUID NOT NULL`
- `version INTEGER NOT NULL`
- `prompt TEXT NOT NULL`
- `explanation TEXT NULL`
- `active BOOLEAN NOT NULL DEFAULT TRUE`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

Recommended constraints:

- `PRIMARY KEY (id, version)`
- `UNIQUE (id, version)`
- `CHECK (version > 0)`

`quiz_questions` has no `mission_id`. Mission ownership is established only through `quiz_mission_questions`.

Recommended version rule:

- immutable released versions
- no update to prompt, explanation, or canonical correctness after publication
- create a new question version for content changes
- active mission links should reference one immutable version

#### `app.quiz_answer_options`

Purpose: canonical answer choices.

Recommended columns:

- `id UUID NOT NULL`
- `question_id UUID NOT NULL`
- `question_version INTEGER NOT NULL`
- `option_key VARCHAR(20) NOT NULL`
- `option_text TEXT NOT NULL`
- `display_order SMALLINT NOT NULL`
- `is_correct BOOLEAN NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

Recommended constraints:

- `PRIMARY KEY (id)`
- `UNIQUE (id, question_id, question_version)`
- `UNIQUE (question_id, question_version, option_key)`
- `UNIQUE (question_id, question_version, display_order)`
- composite FK `(question_id, question_version)` references `quiz_questions(id, version)`

A partial unique index may enforce at most one correct option:

`UNIQUE (question_id, question_version) WHERE is_correct`

At-least-one correct and at-least-two options are not guaranteed by that index.
Before publication is activated, the publication transaction must enforce:

- `COUNT(*) >= 2`
- `COUNT(*) WHERE is_correct = TRUE = 1`
- every option has a unique key
- every option has a unique display order

Preferably implement a database publication trigger or publication procedure that atomically:

1. checks all four conditions
2. sets `active = TRUE` only when all checks pass
3. rejects publication transactionally otherwise

Application validation alone is insufficient if SQL writers can publish a question directly.

The `is_correct` flag must never be serialized to a normal question-delivery response.

#### `app.quiz_mission_questions`

Purpose: map exactly five canonical questions to a mission attempt.

Recommended columns:

- `mission_id UUID NOT NULL`
- `question_id UUID NOT NULL`
- `question_version INTEGER NOT NULL`
- `question_order SMALLINT NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

Recommended constraints:

- `PRIMARY KEY (mission_id, question_order)`
- `UNIQUE (mission_id, question_id, question_version)`
- `UNIQUE (mission_id, question_id, question_version, question_order)`
- FK `mission_id` references `missions(id)`
- composite FK `(question_id, question_version)` references `quiz_questions(id, version)`
- `CHECK (question_order BETWEEN 1 AND 5)`

Application/domain validation must ensure exactly five rows for every active Quiz Mission.

#### `app.quiz_session_questions`

Purpose: immutable snapshot of the five questions assigned to an active attempt.

Recommended columns:

- `session_id UUID NOT NULL`
- `mission_id UUID NOT NULL`
- `question_id UUID NOT NULL`
- `question_version INTEGER NOT NULL`
- `question_order SMALLINT NOT NULL`
- `created_at TIMESTAMP WITH TIME ZONE NOT NULL`

Recommended constraints:

- `PRIMARY KEY (session_id, question_order)`
- `UNIQUE (session_id, question_id, question_version)`
- `UNIQUE (session_id, mission_id, question_id, question_version, question_order)`
- composite FK `(session_id, mission_id)` references `game_sessions(id, mission_id)`
- composite FK `(mission_id, question_id, question_version, question_order)` references
  `quiz_mission_questions(mission_id, question_id, question_version, question_order)`
- `CHECK (question_order BETWEEN 1 AND 5)`

This snapshot is created atomically when the session starts.

Changes to canonical question content after session creation do not alter existing session rows because each session references immutable question version rows.

Newly starting sessions copy the current active mission question version set.

### 4.2 Question membership and order

Question membership is determined by:

1. session mission ID
2. session snapshot in `quiz_session_questions`
3. question ID in the snapshot

The accepted next question is the lowest `question_order` that has no evidence row.

Submission is rejected if:

- question ID is not in the snapshot
- question belongs to another mission
- question position is not the first unanswered position
- session or snapshot does not have exactly five questions

No client-supplied question order is accepted.

### 4.3 Safe question delivery

Recommended endpoint:

```http
GET /api/v1/game-sessions/{sessionId}/quiz/questions
```

Authentication: required.

Response:

```json
{
  "success": true,
  "data": {
    "sessionId": "...",
    "status": "ACTIVE",
    "answeredCount": 2,
    "questions": [
      {
        "questionId": "...",
        "order": 1,
        "prompt": "Question text",
        "options": [
          {
            "optionId": "...",
            "label": "Option A",
            "text": "Answer option text"
          }
        ],
        "answered": true
      }
    ]
  }
}
```

The response must never include:

- `isCorrect`
- correct option ID
- answer key
- score
- reward
- completion status derived outside the backend

The question delivery response may safely show:

- `answered: true`
- previously selected option ID for the owner

It must not expose correctness or explanations in the MVP.

## 5. Answer event contract

### 5.1 Answer submission endpoint

Recommended endpoint:

```http
POST /api/v1/game-sessions/{sessionId}/quiz/answers
```

Request:

```json
{
  "eventId": "f0d8a17e-dc11-4c34-a6b3-3cb0ac407001",
  "questionId": "d5d8e56c-3f86-4e07-a1a8-1234567890ab",
  "selectedOptionId": "7a4b2fd8-a59e-4f54-8d1d-0987654321cd"
}
```

Fields:

- `eventId`: required UUID or equivalent replay identity
- `questionId`: required UUID
- `selectedOptionId`: required UUID

The request must not contain:

- `isCorrect`
- `score`
- `points`
- `completionStatus`
- `completed`
- `reward`
- client timestamp
- question order

### 5.2 First-delivery response

HTTP `201 Created`:

```json
{
  "success": true,
  "data": {
    "sessionId": "...",
    "eventId": "...",
    "questionId": "...",
    "selectedOptionId": "...",
    "answeredCount": 1,
    "answerRecorded": true
  }
}
```

Do not return correctness unless the product explicitly approves it after answer submission.

### 5.3 Duplicate identical delivery

HTTP `200 OK`:

- same `eventId`
- same session
- same question
- same selected option

Return the stored response or an equivalent identical body.

### 5.4 Validation and status codes

| Condition | HTTP | Error code |
|---|---:|---|
| Missing/invalid request fields | 400 | `VALIDATION_ERROR` |
| Authentication missing | 401 | `AUTH_REQUIRED` |
| Session belongs to another player | 403 | `SESSION_ACCESS_DENIED` |
| Session ID missing | 404 | `SESSION_NOT_FOUND` |
| Question ID missing | 404 | `QUIZ_QUESTION_NOT_FOUND` |
| Session not ACTIVE | 409 | `SESSION_NOT_ACTIVE` |
| Session expired | 409 | `SESSION_EXPIRED` |
| Question not in this session | 409 | `QUIZ_QUESTION_NOT_IN_SESSION` |
| Question from another mission | 409 | `QUIZ_QUESTION_NOT_IN_MISSION` |
| Question skipped or out of order | 409 | `QUIZ_QUESTION_ORDER_VIOLATION` |
| Invalid answer option | 422 | `QUIZ_OPTION_INVALID` |
| Same session + same eventId + same canonical request hash | 200 | Stored original response |
| Same session + same eventId + different canonical request hash | 409 | `QUIZ_EVENT_ID_CONFLICT` |
| Different eventId + question already has evidence | 409 | `QUIZ_ANSWER_ALREADY_RECORDED` |
| New eventId + unanswered question submitted out of order | 409 | `QUIZ_QUESTION_ORDER_VIOLATION` |
| Concurrent answer already won | 409 | `QUIZ_ANSWER_ALREADY_RECORDED` |
| Generic gameplay event for a Quiz session | 409 | `QUIZ_ANSWER_ENDPOINT_REQUIRED` |

### 5.5 Duplicate request distinctions

Answer delivery distinguishes three cases:

1. **Delivery replay**

   Same `eventId` and same canonical request hash:

   - no new evidence
   - no score change
   - return the original stored response

2. **Event ID conflict**

   Same `eventId` but a different canonical request hash:

   - return `409 QUIZ_EVENT_ID_CONFLICT`
   - do not update existing evidence
   - do not mutate score or last activity

3. **Already recorded question**

   Different `eventId` for a question that already has evidence:

   - return `409 QUIZ_ANSWER_ALREADY_RECORDED`
   - regardless of the newly selected option
   - do not update existing evidence

If `eventId` is new and the question has no evidence but the question is submitted before
its required fixed-order position, return `409 QUIZ_QUESTION_ORDER_VIOLATION`.

A same event ID with a different request body is never treated as a valid answer change.

### 5.6 Isolation from generic gameplay events

The existing generic `POST /api/v1/game-sessions/{sessionId}/events` endpoint must not create
Quiz evidence or advance Quiz answer state.

For a session whose mission type is QUIZ:

- generic `recordEvent` must reject the request as `QUIZ_ANSWER_ENDPOINT_REQUIRED`
- generic events must not increment `eventCount`
- generic events must not update `lastActivityAt`
- generic events must not change `MissionProgress.progressValue`
- generic events must not create answer evidence
- generic events must not complete a Mission

Quiz answer submission is permitted only through the dedicated
`POST /api/v1/game-sessions/{sessionId}/quiz/answers` endpoint.

This isolation prevents generic event payloads from bypassing answer ordering,
option validation, evidence immutability, or canonical answer scoring.

## 6. Immutable evidence and idempotency

### 6.1 Recommended dedicated evidence table

The existing `game_sessions.metadata` and generic event endpoint are insufficient.

V10 should add the following coherent PostgreSQL model. This section proposes exact keys and composite foreign keys; it is not SQL to execute.

The canonical definitions in section 4.1 are authoritative for `quiz_questions`,
`quiz_answer_options`, `quiz_mission_questions`, and `quiz_session_questions`.
No duplicate mission ownership is stored in `quiz_questions`.

#### Existing `game_sessions` integrity extension

- existing primary key: `PRIMARY KEY (id)`
- V10 adds: `UNIQUE (id, mission_id)`

This composite unique key is required so that a session snapshot can prove both
the session and its Mission.

#### `quiz_session_questions`

Exact snapshot integrity:

- `PRIMARY KEY (session_id, question_order)`
- `UNIQUE (session_id, question_id, question_version)`
- `UNIQUE (session_id, mission_id, question_id, question_version, question_order)`
- composite FK `(session_id, mission_id)` references `game_sessions(id, mission_id)`
- composite FK `(mission_id, question_id, question_version, question_order)`
  references `quiz_mission_questions(mission_id, question_id, question_version, question_order)`

This prevents a snapshot from assigning a Mission that is different from the Mission in `game_sessions`.

The session snapshot is insert-only. It is created atomically with session start and may be backfilled by the implementation migration only for sessions not yet defined.

#### `quiz_answer_evidence`

- `id UUID NOT NULL`
- `session_id UUID NOT NULL`
- `mission_id UUID NOT NULL`
- `question_id UUID NOT NULL`
- `question_version INTEGER NOT NULL`
- `question_order SMALLINT NOT NULL`
- `selected_option_id UUID NOT NULL`
- `event_id UUID NOT NULL`
- `request_hash TEXT NOT NULL`
- `response_json TEXT NOT NULL`
- `is_correct BOOLEAN NOT NULL`
- `points SMALLINT NOT NULL`
- `recorded_at TIMESTAMP WITH TIME ZONE NOT NULL`

Primary key:

- `PRIMARY KEY (id)`

Unique keys:

- `UNIQUE (session_id, question_id, question_version)`
- `UNIQUE (session_id, question_order)`
- `UNIQUE (session_id, event_id)`
- `UNIQUE (session_id, mission_id, question_id, question_version, question_order)`

Foreign keys:

- `session_id` references `game_sessions(id)`
- `(session_id, mission_id, question_id, question_version, question_order)` references
  `quiz_session_questions(session_id, mission_id, question_id, question_version, question_order)`
- `(selected_option_id, question_id, question_version)` references
  `quiz_answer_options(id, question_id, question_version)`

A database CHECK requires `points IN (0,20)`. Server application logic and a recomputation check require `is_correct` to match the selected option and `points = is_correct ? 20 : 0`.

Evidence fields:

- session ID
- question ID and immutable version
- question order
- selected option ID
- event ID
- server-derived correctness
- server-derived 20/0 points
- request hash
- recorded timestamp

Evidence must be insert-only. No update or delete service operation is allowed.

### 6.2 Database uniqueness

Required uniqueness:

- one evidence row per `(session_id, question_id, question_version)`
- one evidence row per `(session_id, question_order)`
- one evidence row per `(session_id, event_id)`
- exact option membership by `(selected_option_id, question_id, question_version)`
- exact session snapshot membership by `(session_id, mission_id, question_id, question_version, question_order)`

Independent foreign keys are not sufficient because they permit an option from another question or a question outside the session snapshot. The composite foreign keys above are mandatory.

### 6.2.1 Evidence immutability

Application behavior:

- evidence repositories expose insert/save-and-flush operations only
- no update method
- no delete method
- no event/controller endpoint can modify a recorded answer
- no service operation may change `selected_option_id`, `is_correct`, `points`, `question_version`, or `question_order`
- only expiration, completion, and reward services may change different tables

Database protection:

- evidence primary/unique keys prevent second answer or replay row
- composite foreign keys prevent cross-question/cross-session evidence
- `CHECK(points IN (0,20))`
- no database trigger or service is required to let evidence change after insert
- revoke update/delete permissions from the application database role if operational policy can separate DDL/DML; otherwise JPA repository/service discipline plus unique constraints is the minimum protection
- a trigger or RLS immutability rule may be added later only if accidental SQL updates are a verified risk

Question and option immutability:

- released question/option rows are insert-only
- question text, explanation, option text, and `is_correct` cannot be updated through the application
- mission question links cannot be silently repointed for an existing session
- active session snapshots store the exact immutable version

If content changes, create a new question version and new mission links; do not mutate existing evidence or session snapshots.

### 6.3 Answer idempotency

Answer delivery uses `eventId`; Answer API has no separate `Idempotency-Key` header.

- `eventId` is unique within a session
- canonical request hash covers `eventId`, `sessionId`, `questionId`, and `selectedOptionId`
- evidence stores the original canonical request hash and original response JSON
- replay checks occur before session next-question/order validation
- insert evidence and its stored response atomically

The existing `app.idempotency_records` table is not used for answer evidence. It cannot safely hold one answer per answer because of `(player_id, session_id, operation_name)` uniqueness. Answer replay state belongs in `quiz_answer_evidence`, whose `(session_id, event_id)` uniqueness identifies delivery.

For a completion idempotency record:

- `operation_name = "QUIZ_MISSION_COMPLETION"`
- `session_id`
- `player_id`
- `mission_id`
- request hash
- response JSON
- status

### 6.4 Answer replay decision flow

For every Answer API request:

1. Validate authentication and `eventId`, `questionId`, `selectedOptionId`.
2. Lock and load the owning session under `(sessionId, playerId)`.
3. Look up existing evidence by `(session_id, event_id)` **before expiration/status validation**.
4. If replay row exists:
   - compute canonical request hash
   - if hash differs, return `409 QUIZ_EVENT_ID_CONFLICT`
   - if hash matches, return the stored response JSON with `200 OK`
   - do not recheck next-unanswered order
   - do not mutate evidence or update last activity
5. If no replay row exists:
   - check expiration and ACTIVE status
   - if overdue and ACTIVE, persist `EXPIRED`, set `endedAt`, flush/commit the session-status change, and reject `SESSION_EXPIRED`
   - if terminal status is already EXPIRED/COMPLETED/ABANDONED, reject with the stable terminal-state code
   - validate question belongs to the session snapshot
   - validate the question belongs to the mission
   - look up evidence by `(session_id, question_id, question_version)`
   - if evidence exists, return `409 QUIZ_ANSWER_ALREADY_RECORDED`
   - only if no evidence exists, validate it is the first unanswered question in fixed order
   - validate selected option belongs to the exact question version
   - insert evidence and response atomically
   - return `201 Created`

The replay check intentionally precedes session ordering. Therefore replaying an answer for question 1 after question 5 has been answered returns the original response instead of `QUIZ_QUESTION_ORDER_VIOLATION`.

#### Answer expiration transaction semantics

The Answer API must use a transaction boundary that distinguishes:

- a normal validation failure; and
- the dedicated expired-session rejection.

When no event replay exists and the session is ACTIVE but overdue:

1. the pessimistically locked session is changed to `EXPIRED`;
2. `endedAt` is set to the same Clock instant;
3. the session status is flushed as part of the transaction;
4. a dedicated `SessionExpiredException` is thrown;
5. that exception is configured as `noRollbackFor` (or handled by an equivalent dedicated
   transaction boundary) so the `ACTIVE -> EXPIRED` commit is retained;
6. no answer evidence is inserted;
7. no score, mission progress, or last-activity value is changed;
8. the API returns `409 SESSION_EXPIRED`.

The `noRollbackFor` exception policy must be narrow and must not apply to normal
validation failures. Invalid question membership, invalid option, already-recorded evidence,
out-of-order submission, hash conflict, and ownership failures remain normal transaction
rollbacks and do not persist evidence or status changes.

Terminal states already set to `EXPIRED`, `COMPLETED`, or `ABANDONED` are rejected without
changing state. Rejected answer replay by a different player is blocked before evidence lookup.

## 7. Server-side scoring

Scoring is deterministic:

- `score = sum(points for exactly 5 evidence rows)`
- correct evidence adds 20
- incorrect evidence adds 0
- maximum 100
- only persisted evidence is counted

Example:

| Evidence | Correct | Points |
|---|---:|---:|
| Q1 | yes | 20 |
| Q2 | no | 0 |
| Q3 | yes | 20 |
| Q4 | yes | 20 |
| Q5 | no | 0 |
| Total | 3 correct | 60 |

Completion must recalculate score from evidence. It must not use:

- `finalScore`
- `durationSeconds`
- event payload score
- metadata
- mission progress progress value

Quiz score and Mission reward are separate values:

- Quiz score is an evaluation result from 0 to 100
- Mission reward is determined only by existing `Mission.rewardPoints` and `Mission.rewardCode`
- A Quiz score of 100 does not imply that reward points equal 100
- Existing mission reward configuration remains unchanged
- Reward policy and reward types are not redesigned in this phase

The server should derive `isCorrect` by loading the selected option and checking `is_correct`.

The evidence row stores the derived result, but completion may recompute from canonical options and evidence as an additional integrity check.

## 8. Completion transaction design

### 8.1 Completion endpoint

```http
POST /api/v1/game-sessions/{sessionId}/complete
Idempotency-Key: <unique client key>
```

Request:

```json
{
  "metadata": {
    "source": "quiz-ui"
  }
}
```

Metadata is diagnostic only and must never influence:

- correctness
- score
- mission progress
- reward
- completion eligibility

Recommended implementation: remove `finalScore` and `durationSeconds` from the completion DTO.

Unknown client fields should be rejected instead of silently ignored.

### 8.2 Completion validation

Under a transaction:

1. authenticate and derive player ID
2. pessimistically lock session by `(sessionId, playerId)`
3. look up the completion idempotency record
4. if the same key and canonical request hash exist, return its stored original response
5. if the same key exists with a different hash, return `409 IDEMPOTENCY_KEY_CONFLICT`
6. if no completion record exists, recheck expiration using one Clock instant
7. if overdue and ACTIVE:
   - change the locked session to `EXPIRED`;
   - set `endedAt`;
   - flush/commit the session-status transition;
   - throw a dedicated `SessionExpiredException`;
   - reject the completion with `409 SESSION_EXPIRED`;
   - do not lock MissionProgress or PlayerProgress, and do not insert reward/progress/idempotency completion changes
8. require status ACTIVE
9. load mission and require type QUIZ
10. load session Quiz snapshot
11. require exactly five snapshot questions
12. load exactly five answer evidence rows
13. require one evidence row for every snapshot position 1–5
14. verify evidence belongs to session and mission
15. verify no evidence question belongs outside the snapshot
16. validate each selected option
17. lock MissionProgress by `(playerId, missionId)`
18. initialize PlayerProgress safely using `UNIQUE(player_id)` and conflict-safe insertion
19. lock PlayerProgress by `(playerId)`
20. recalculate score 0–100 from immutable evidence
21. mark MissionProgress COMPLETED and set completion timestamps
22. mark session COMPLETED and set endedAt
23. insert the existing Mission reward after score/evidence validation
24. recalculate and persist PlayerProgress
25. persist completion idempotency record and original response atomically

For every transaction step, no reward or mission-progress update occurs if a required precondition fails.

### 8.3 Idempotency

Completion replay occurs before expiration and ACTIVE-state validation:

- same key + same canonical request hash -> return stored response
- same key + different request hash -> `409 IDEMPOTENCY_KEY_CONFLICT`
- different key + already COMPLETED session -> `409 SESSION_ALREADY_COMPLETED`
- concurrent completion -> only one row/lock winner

This ordering allows a successful retry to receive the original completion response even if the session is now COMPLETED.

The existing `uk_idempotency_records_session` constraint should remain compatible with one completion record per `(player, session, operation)`.

### 8.4 Rollback

The transaction design must separate normal validation failures from expiration rejection:

- Normal validation/reward/progress failures use standard transaction rollback semantics.
- Invalid evidence, missing answers, invalid mission/snapshot, reward insertion failure,
  MissionProgress failure, and PlayerProgress failure roll back all completion-related changes.
- Overdue ACTIVE completion is handled as a lifecycle exception, not a normal validation failure:
  - the transaction flushes `ACTIVE -> EXPIRED` and `endedAt`;
  - a dedicated `SessionExpiredException` is marked `noRollbackFor` or is handled by a
    dedicated transaction boundary;
  - the session transition commits even though the request returns `409 SESSION_EXPIRED`;
  - MissionProgress remains unchanged;
  - PlayerProgress remains unchanged;
  - no reward is inserted;
  - no completion idempotency response is stored.
- The expiration transition must be committed before the request rejection is observed.
- Normal terminal states already set to `EXPIRED`, `COMPLETED`, or `ABANDONED` are rejected
  without changing lifecycle state. A replay with a matching completion key may return the
  stored completion response before terminal-state validation.

Reward, MissionProgress, PlayerProgress, session completion, and completion idempotency
persistence remain in one atomic transaction for a successful completion.

### 8.5 Reward

Recommended mission reward:

- `reward_source = MISSION_COMPLETION`
- `session_id = completed session`
- existing mission reward points/reward code
- existing `reward_type = MISSION_COMPLETE`

The DB unique reward index remains a fallback.

No reward is issued for:

- missing answer
- invalid evidence
- expired session
- terminal session
- evidence validation failure

## 9. Test matrix

| Area | Scenario | H2 | PostgreSQL |
|---|---|---:|---:|
| Score | five correct answers | Yes | Yes |
| Score | five incorrect answers | Yes | Yes |
| Score | mixed correct/incorrect | Yes | Yes |
| Score boundary | score 0 | Yes | Yes |
| Score boundary | score 100 | Yes | Yes |
| Completion | missing answer | Yes | Yes |
| Evidence | invalid question ID | Yes | Yes |
| Evidence | question from another mission | Yes | Yes |
| Evidence | invalid answer option | Yes | Yes |
| Evidence | option from another question | Yes | Yes |
| Delivery | same eventId and same request hash | Yes | Yes |
| Delivery | replay earlier event after later questions | Yes | Yes |
| Delivery | same eventId with different request hash | Yes | Yes |
| Delivery | successful answer followed by session expiration then identical retry | Yes | Yes |
| Delivery | successful answer followed by session completion then identical retry | Yes | Yes |
| Delivery | same eventId with different payload after session expiration | Yes | Yes |
| Delivery | replay from a different player | Yes | Yes |
| Delivery | different eventId for an already answered question, regardless of option | Yes | Yes |
| Delivery | different eventId for an unanswered question out of order | Yes | Yes |
| Delivery | Q1 eventId A answered, then Q1 eventId B submitted | Yes | Yes |
| Delivery | Q1 eventId A answered, then Q2 submitted before its turn | Yes | Yes |
| Delivery | Q1 eventId A replayed with same body and hash | Yes | Yes |
| Delivery | Q1 eventId A reused with different body and hash | Yes | Yes |
| Delivery | concurrent first submission | Limited | Required |
| Delivery | generic event for Quiz session | Yes | Yes |
| Snapshot | mission has fewer/more than 5 question links | Yes | Yes |
| Snapshot | session has fewer/more than 5 snapshot rows | Yes | Yes |
| Publication | zero correct options | Yes | Yes |
| Publication | two correct options | Yes | Yes |
| Publication | fewer than two options | Yes | Yes |
| Order | skipped first question | Yes | Yes |
| Order | out-of-order submission | Yes | Yes |
| Ownership | cross-player access | Yes | Yes |
| Lifecycle | expired session | Yes | Yes |
| Lifecycle | terminal session | Yes | Yes |
| Transaction | overdue Answer persists EXPIRED and rolls back answer evidence | Yes | Required |
| Transaction | normal Answer validation failure rolls back evidence | Yes | Yes |
| Security | forged `finalScore` | Yes | Yes |
| Security | forged `isCorrect` | Yes | Yes |
| Security | forged completion/reward fields | Yes | Yes |
| Idempotency | duplicate completion | Yes | Yes |
| Idempotency | same key/different request | Yes | Yes |
| Idempotency | replay successful completion after session is COMPLETED | Yes | Yes |
| Idempotency | replay completion after same session expires | Yes | Yes |
| Idempotency | overdue ACTIVE completion persists EXPIRED and rejects | Yes | Required |
| Reward | duplicate completion no duplicate reward | Yes | Required |
| Reward | score 100 does not imply reward 100 | Yes | Yes |
| Transaction | completion rollback | Yes | Yes |
| PostgreSQL | concurrent answer submission | No | Required |
| PostgreSQL | concurrent completion | No | Required |
| PostgreSQL | concurrent event vs scheduler | No | Required |
| PostgreSQL | stale terminal write cannot restore ACTIVE | No | Required |
| Progress | player progress concurrent update | No | Required |

H2 is suitable for DTO validation, canonical scoring logic, transaction unit/integration behavior, and event contract tests.

PostgreSQL is required for:

- unique evidence constraints
- pessimistic locking
- concurrent answer submissions
- concurrent completion
- session versus scheduler races
- terminal-state immutability
- reward uniqueness
- player progress consistency

## 10. Implementation work packages

### A. Canonical Quiz Question Model

Objective:

- store canonical Quiz Missions, questions, options, and question ordering

Reuse:

- `Mission`
- `MissionType.QUIZ`
- Flyway
- JPA/Jakarta validation

Likely files/classes:

- V10 migration
- quiz entities
- quiz repositories
- mission-to-question repository/service
- question snapshot service

API impact:

- no direct public write/admin API required for MVP
- question delivery endpoint

Migration impact:

- V10 tables for questions, options, mission questions, and session snapshot

Tests:

- exactly five questions per mission
- one correct option
- immutable version
- active session snapshot

Dependencies:

- approved actual five questions and answer options
- historical content review

### B. Question Delivery Contract

Objective:

- expose exactly one safe question contract

Reuse:

- authenticated session owner lookup
- session question snapshot
- existing API response envelope

Likely files/classes:

- `GameSessionController`
- quiz DTOs
- quiz service/repository

API impact:

- `GET /api/v1/game-sessions/{sessionId}/quiz/questions`

Migration impact:

- no additional schema beyond work package A

Tests:

- no correct answer exposure
- ownership
- expired/terminal session
- answered count

Risks:

- leaking correct options
- snapshot mismatch

### C. Immutable Answer Evidence

Objective:

- persist exactly one valid answer per session/question

Reuse:

- pessimistic session lock
- existing session ACTIVE/EXPIRED checks
- Clock

Likely files/classes:

- answer evidence entity/repository
- answer event DTO
- quiz answer service

API impact:

- `POST /api/v1/game-sessions/{sessionId}/quiz/answers`

Migration impact:

- V10 answer evidence and session snapshot constraints

Tests:

- duplicate delivery
- already answered
- option/question membership
- ordering
- concurrent submissions

Risks:

- inconsistent evidence if evidence and snapshot are not transactionally linked
- option FK from wrong question

### D. Answer Validation and Idempotency

Objective:

- implement duplicate/replay distinction and stable errors

Reuse:

- API exception/error envelope
- row locks
- request hash pattern

Likely files/classes:

- answer service
- answer evidence repository
- `IdempotencyRecord` only if completion
- `GlobalExceptionHandler`

API impact:

- eventId-based replay/conflict behavior
- no separate Idempotency-Key header
- stable HTTP/error codes

Migration impact:

- evidence unique constraints and request hash
- no immediate change to `idempotency_records` if answer uses evidence event IDs

Tests:

- same request replay
- different request conflict
- concurrent requests
- invalid key

### E. Server-Side Scoring

Objective:

- derive a 0–100 score from persisted evidence

Reuse:

- canonical option `is_correct`
- evidence
- mission snapshot

Likely files/classes:

- quiz scoring service
- evidence repository

API impact:

- completion response includes server-calculated score

Migration impact:

- none if evidence schema already has server correctness/points

Tests:

- 0 and 100
- mixed/missing evidence
- client score rejection

Risks:

- trusting persisted points instead of canonical data
- evidence tampering

### F. Atomic Mission Completion

Objective:

- validate evidence and complete session atomically

Reuse:

- pessimistic session lock
- expiration checks
- mission progress repository
- completion idempotency table

Likely files/classes:

- `CompleteGameSessionRequest`
- `GameSessionController`
- `GameSessionService`
- `MissionProgressService`

API impact:

- remove/forbid client score fields
- require completion Idempotency-Key
- return server score

Migration impact:

- only V10 evidence/snapshot; no separate completion table required

Tests:

- missing answers
- duplicate completion
- rollback
- expired/terminal
- concurrent completion

### G. Reward and Player Progress Consistency

Objective:

- issue exactly one reward and update player progress once evidence is complete

Reuse:

- reward uniqueness constraints
- mission progress lock
- player progress recalculation

Player progress concurrency:

- existing V3 `app.player_progress.player_id` is already `UNIQUE`
- keep that unique constraint as the one-row invariant
- if no row exists, initialize it inside the completion transaction
- use a database-safe insert strategy: transactionally insert by player ID with `ON CONFLICT (player_id) DO NOTHING`, then pessimistically lock and load the row
- do not call `ensureForPlayer` through an unguarded `save()` race
- if concurrent completion transactions first find no row, only one insert wins; both then use the same locked row
- add `findForUpdateByPlayerId` using `PESSIMISTIC_WRITE`
- lock session `(id,player)` first, mission progress `(player,mission)` second, and player progress `(player)` third
- recompute all derived totals while the player progress row is locked
- do not run `recalculateForPlayer` without the row lock
- PostgreSQL completion transactions for different missions serialize at the player progress row
- the second transaction re-runs mission/reward-derived calculations after acquiring the row lock
- serialize or consistently lock mission progress by player/mission and player progress by player in every service flow that updates both
- avoid lock ordering where player progress is locked before session or mission progress

No new architecture or distributed lock is required. Existing modular-monolith repositories and transactions are sufficient.

Repository inspection finding:

- the current `PlayerProgressRepository` has no pessimistic lookup
- the current `PlayerProgressService.ensureForPlayer` may save/create outside the lock strategy
- the current completion flow locks the session and mission progress before `recalculateForPlayer`

This design is safe only if all updating flows adopt the session -> mission progress -> player progress order and initialization uses the unique player ID. The implementation audit must inspect every future/new call path for reverse lock ordering before implementation is approved.

Likely files/classes:

- `PlayerRewardService`
- `PlayerRewardRepository`
- `PlayerProgressService`
- `PlayerProgressRepository`

API impact:

- no separate reward endpoint change

Migration impact:

- optional PlayerProgress row lock only; no schema requirement

Tests:

- reward uniqueness
- concurrent completion
- player progress totals
- rollback

### H. Integration and PostgreSQL Concurrency Verification

Objective:

- verify the vertical slice against real PostgreSQL behavior

Reuse:

- existing disposable PostgreSQL test pattern
- existing `Clock`
- existing row-lock patterns

Likely files/classes:

- opt-in PostgreSQL test class
- H2 integration test class

API impact:

- HTTP integration tests for answer and completion endpoints

Migration impact:

- V10 applied on fresh database

Tests:

- Flyway V1–V10
- Hibernate validate
- answer/completion concurrency
- stale terminal writes
- reward and progress final state

## 11. Risks and unresolved decisions

### Risks

- canonical question content and historical review are not yet present
- V10 exact table names and constraint syntax require implementation review
- generic event endpoint code may remain callable as a bypass if it is not restricted or deprecated for Quiz evidence
- persisted evidence can drift if canonical question/option versions are mutable
- current completion DTO may accept unknown fields unless strict deserialization is implemented
- PlayerProgress lost updates remain possible until a pessimistic row lookup is added
- reward row/session linkage must be implemented consistently with existing reward uniqueness

### Resolved design decisions

1. Question order is fixed 1–5 and no randomization or skipping is allowed.
2. Active Quiz Missions require exactly five mission-question links.
3. Active sessions contain exactly five immutable session snapshot records.
4. Completion requires exactly one evidence record for every snapshot position.
5. Previously answered selected options may be returned to the session owner.
6. No correctness or explanations are returned in the MVP question-delivery response.
7. Mission reward points/code remain from existing Mission reward configuration.
8. Completion response JSON is stored and replayed by completion Idempotency-Key.
9. Answer replay identity is `eventId`; Answer API does not use `Idempotency-Key`.

The only unresolved item is actual canonical Quiz content and final V10 implementation syntax. These are inputs or implementation details, not unresolved Quiz contract decisions.

## Final Design Consistency Checklist

- [x] Exactly five questions, fixed order 1–5, no skipping or reordering.
- [x] One immutable answer per question; replay identity is `eventId`.
- [x] Answer API does not require a separate `Idempotency-Key`.
- [x] Completion API requires `Idempotency-Key`.
- [x] Completion replay lookup occurs after session lock and before expiration/status validation.
- [x] Answer replay lookup occurs after session lock, ownership validation, and before expiration/status validation.
- [x] Answer replay never mutates evidence, score, or last activity.
- [x] Answer replay from another player is rejected before evidence lookup.
- [x] Same eventId and same request hash returns the original delivery response.
- [x] Same eventId and different request hash returns `QUIZ_EVENT_ID_CONFLICT`.
- [x] Different eventId for an already evidenced question returns `QUIZ_ANSWER_ALREADY_RECORDED` regardless of selected option.
- [x] Different eventId for an unanswered question submitted out of order returns `QUIZ_QUESTION_ORDER_VIOLATION`.
- [x] Evidence uniqueness is checked before first-unanswered-order validation.
- [x] Same completion key with the same hash returns stored response.
- [x] Same completion key with a different hash returns `409 IDEMPOTENCY_KEY_CONFLICT`.
- [x] Overdue ACTIVE completion persists `EXPIRED` and rejects before completion/reward.
- [x] Answer/Completion lifecycle expiration uses a dedicated no-rollback exception or transaction boundary; normal validation failures remain rollback.
- [x] Overdue ACTIVE Answer persists `EXPIRED` and rejects without answer evidence or activity updates.
- [x] Expiration rejection commits only the lifecycle transition; normal validation failures roll back transaction changes.
- [x] Completion and Answer use dedicated SessionExpiredException/noRollback semantics compatible with Phase 2.4.2.3.
- [x] Section 5.4 duplicate matrix distinguishes replay, event conflict, already recorded question, and out-of-order new event.
- [x] Generic gameplay events cannot create Quiz evidence or advance Quiz state.
- [x] `quiz_questions` has canonical immutable `(id, version)` primary key and no redundant mission ownership.
- [x] Every composite FK column type and order references an existing primary or unique key.
- [x] `game_sessions(id, mission_id)` composite uniqueness supports session-mission integrity.
- [x] Session snapshot requires the exact mission, question version, and question order.
- [x] Evidence requires exact session snapshot membership and exact option version membership.
- [x] Evidence is insert-only and cannot be updated or deleted by service operations.
- [x] Question publication requires at least two options and exactly one correct option.
- [x] PlayerProgress initialization uses unique player ID and conflict-safe insertion.
- [x] Completion lock ordering is session -> mission progress -> player progress.
- [x] MissionProgress is locked before PlayerProgress initialization/locking.
- [x] Session status updates, MissionProgress updates, reward insertion, PlayerProgress recalculation, and completion idempotency persistence occur after required locks.
- [x] Quiz score and Mission reward remain separate values.
- [x] Mission reward remains configured by existing Mission reward fields.
- [x] GameSave remains out of scope.

## 12. Final recommendation

**READY FOR IMPLEMENTATION**

The design is sufficiently complete for a first implementation slice after approval of:

- the five canonical Quiz questions
- their answer options and correct options
- historical content review
- final V10 table names/constraints
- answer/completion HTTP DTO names

The implementation should start with canonical Quiz question model and answer evidence, then server score and completion, not with frontend, GameSave, or other mission types.
