# PHASE 2.4.2 — PRE-IMPLEMENTATION AUDIT

## 1. Audit scope

Repository: `23130346-MinhTri/Viet_Su_Ky`

Working directory: `D:\Games\History`

This audit inspects the current repository before any Phase 2.4.2 application or database change. The user-supplied Phase 2.4.2 specification was treated as the phase authority. Existing Phase 2.4.1 reports were compared with source and migration files rather than assumed to reflect the current state.

## 2. Executive findings

**Audit status: BLOCKED PENDING APPROVAL**

The mission and session foundation already exists in source, but it is incomplete for the approved Phase 2.4.2 scope:

1. Mission list/detail and availability endpoints exist.
2. Session create/get/event/complete/abandon endpoints exist.
3. Lazy mission-progress initialization exists.
4. Ownership checks are used in session service methods.
5. Authentication is enforced by global Spring Security.
6. A forward-only database migration is required before safe session-start concurrency implementation.
7. Existing code does not prevent duplicate ACTIVE sessions at the database level.
8. `MissionService` computes availability, but mission list/detail DTOs do not expose the approved availability/progress fields consistently.
9. Session creation does not perform a duplicate active-session check.
10. Session completion currently trusts client-supplied `finalScore`; implementing completion is outside this phase and the existing endpoint must not be expanded.

## 3. Existing implementation inventory

### Mission module

- `mission/controller/MissionController.java`
- `mission/dto/MissionResponse.java`
- `mission/dto/MissionAvailabilityResponse.java`
- `mission/entity/Mission.java`
- `mission/enums/MissionStatus.java`
- `mission/enums/MissionType.java`
- `mission/repository/MissionRepository.java`
- `mission/service/MissionService.java`

Existing endpoints:

- `GET /api/v1/missions`
- `GET /api/v1/missions/{missionId}`
- `GET /api/v1/missions/{missionId}/availability`

The detail endpoint does not have an `@AuthenticationPrincipal` parameter, but the global security configuration requires authentication for all routes except health and authentication endpoints.

### Session module

- `session/controller/GameSessionController.java`
- `session/dto/CreateGameSessionRequest.java`
- `session/dto/GameSessionResponse.java`
- `session/entity/GameSession.java`
- `session/enums/SessionStatus.java`
- `session/repository/GameSessionRepository.java`
- `session/service/GameSessionService.java`

Existing endpoints:

- `POST /api/v1/game-sessions`
- `GET /api/v1/game-sessions/{sessionId}`
- `POST /api/v1/game-sessions/{sessionId}/events`
- `POST /api/v1/game-sessions/{sessionId}/complete`
- `POST /api/v1/game-sessions/{sessionId}/abandon`

Phase 2.4.2 scope requires only session creation and retrieval in addition to mission query. Event, complete, and abandon endpoints are existing out-of-scope behavior and must not be extended or redesigned in this phase.

### Progress module

- `progress/entity/MissionProgress.java`
- `progress/enums/MissionProgressStatus.java`
- `progress/repository/MissionProgressRepository.java`
- `progress/service/MissionProgressService.java`

The `(player_id, mission_id)` database uniqueness constraint already exists. Lazy initialization is available through `ensureForPlayer`.

## 4. Existing schema

Verified migrations:

- V1: application schema
- V2: account and player profile
- V3: missions, game sessions, mission progress, player progress, rewards, and game saves
- V4: Phase 2.4 enum compatibility, reward integrity additions, idempotency table
- V5: reward source-reference constraint
- V6: strengthened reward source-reference constraint
- V7: mission type default alignment

V1–V7 must remain unchanged.

Relevant existing constraints:

- Mission unique code
- Mission prerequisite foreign key
- Mission progress unique `(player_id, mission_id)`
- Game session state check
- Game session player and mission foreign keys
- Player and mission indexes
- No unique/partial unique constraint preventing duplicate ACTIVE sessions for the same player and mission

The entity inventory also aligns with PostgreSQL according to the Phase 2.4.1.6 report, but no Phase 2.4.2 PostgreSQL verification has been performed in this audit.

## 5. Mission availability rules

Existing approved rule:

- An inactive mission is unavailable.
- A mission with no prerequisite is available.
- A mission with a prerequisite is available only when the player's prerequisite progress status is `COMPLETED`.
- Existing mission progress status is returned directly; this means `IN_PROGRESS` or `COMPLETED` may be returned by availability resolution.
- Missing mission progress is not interpreted as completed and is not written during GET requests.

No rule beyond prerequisites and active status is present.

## 6. Session state machine

The entity and database support:

- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

The service currently allows:

- Session creation as `ACTIVE`
- Event recording only from `ACTIVE`
- Completion only from `ACTIVE`
- Abandonment only from `ACTIVE`

Terminal states are not transitioned back to `ACTIVE`.

## 7. Transaction boundaries

`createSession`, `recordEvent`, `completeSession`, and `abandonSession` use `@Transactional` where implemented.

Session creation transactionally calls:

1. Player lookup
2. Mission lookup
3. Mission availability validation
4. Mission progress lazy initialization/update
5. GameSession save

No database constraint currently protects the duplicate-session invariant.

## 8. Security and ownership

- Spring Security globally requires authentication for application endpoints.
- JWT principal is converted to `Account` and then to `PlayerProfile`.
- Session lookup uses `findByIdAndPlayerId`.
- Session mutations pass the authenticated player ID into service methods.
- Mission list/detail/availability resolve player identity from principal.
- The create-session request contains only `missionId`.

No client-supplied player/account identity is accepted for session creation.

## 9. Concurrency risk

**High risk:** a Java pre-check or `find` query cannot prove one ACTIVE session per player per mission.

Two concurrent POST requests can:

1. Both pass availability checks.
2. Both initialize mission progress.
3. Both insert `ACTIVE` sessions.
4. Return duplicate sessions.

The existing schema has no partial unique index for this invariant.

The required safe design is a forward-only migration V8 with a PostgreSQL partial unique index:

`UNIQUE (player_id, mission_id) WHERE status = 'ACTIVE'`

This is a database-side concurrency guarantee. Application duplicate detection should remain user-friendly, but must not replace the database constraint.

## 10. Missing functionality

- Mission list DTO lacks player availability and progress status fields.
- Mission detail DTO lacks player availability and progress status fields.
- Mission detail does not use the requested player-aware DTO and availability rule in its current implementation.
- Session creation has no duplicate active-session check or conflict mapping.
- Session creation does not return HTTP `201 Created`.
- Session creation does not detect PostgreSQL unique-index violations safely and consistently.
- Integration coverage is insufficient for:
  - locked and missing missions
  - lazy progress
  - no mission GET writes
  - duplicate active-session concurrency
  - transaction rollback
  - session owner/cross-player API behavior
- Error responses do not expose the Phase 2.4 error catalog or stable error codes.

## 11. Out-of-scope findings

The existing completion endpoint currently accepts client `finalScore`. This is contrary to the security rules of the broader design but is explicitly out of scope for this phase. It must not be hardened or completed as part of 2.4.2 unless separately approved.

Events and abandon behavior already exist. This audit does not propose changing them.

## 12. Exact proposed files

### Migration

- `backend/src/main/resources/db/migration/V8__active_session_uniqueness.sql`

### Mission files

- `backend/src/main/java/com/vietsuuky/mission/dto/MissionResponse.java`
- `backend/src/main/java/com/vietsuuky/mission/controller/MissionController.java`
- `backend/src/main/java/com/vietsuuky/mission/service/MissionService.java`

Potentially:

- `backend/src/main/java/com/vietsuuky/mission/dto/MissionAvailabilityResponse.java`

### Session files

- `backend/src/main/java/com/vietsuuky/session/controller/GameSessionController.java`
- `backend/src/main/java/com/vietsuuky/session/dto/GameSessionResponse.java`
- `backend/src/main/java/com/vietsuuky/session/repository/GameSessionRepository.java`
- `backend/src/main/java/com/vietsuuky/session/service/GameSessionService.java`

Potentially:

- `backend/src/main/java/com/vietsuuky/common/exception/ApiException.java`
- `backend/src/main/java/com/vietsuuky/common/exception/GlobalExceptionHandler.java`
- `backend/src/main/java/com/vietsuuky/common/response/ApiResponse.java`

### Tests

- `backend/src/test/java/com/vietsuuky/mission/MissionApiIntegrationTest.java`
- `backend/src/test/java/com/vietsuuky/session/GameSessionApiIntegrationTest.java`
- `backend/src/test/java/com/vietsuuky/session/GameSessionConcurrencyIntegrationTest.java`
- `backend/src/test/java/com/vietsuuky/Phase242PostgresIntegrationTest.java`

Existing test classes may be extended instead of creating duplicate test coverage.

## 13. Audit result

No application code or database migration was modified during Step A.

Implementation must stop at this point because a required forward-only schema migration is needed to guarantee session concurrency. A migration must be approved before Step C.
