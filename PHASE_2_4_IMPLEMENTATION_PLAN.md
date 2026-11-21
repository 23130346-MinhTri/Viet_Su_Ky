# PHASE 2.4 — FINAL DESIGN CONSISTENCY REVIEW

This Phase 2.4 design review is complete and accepted conditionally.

This document records the final consistency updates and confirms that no production implementation or database change has been performed during the review.

## Final accepted design decisions

- Game session lifecycle uses `ACTIVE`, `COMPLETED`, `ABANDONED`, `EXPIRED`.
- Mission progress uses `AVAILABLE`, `LOCKED`, `IN_PROGRESS`, `COMPLETED`.
- Mission type uses `QUIZ`, `EXPLORATION`, `STRATEGY`, `STORY`, `SIMULATION`.
- Client must not submit authoritative completion fields such as `finalScore`, `rewardPoints`, `isCompleted`, `missionStatus`, `completedAt`.
- Completion must be idempotent and protected by `idempotency_records` + DB uniqueness.
- Rewards are stored in `player_rewards` with `reward_source`, `source_reference`, `session_id`, and `reward_type`, allowing repeatable and one-time rewards to coexist safely.
- Mission progress is initialized lazily rather than pre-created for every player/mission pair.
- `game_saves` are versioned and update-only unless a creation endpoint is explicitly added.
- Mission list availability is computed without writing to the database for every mission row.
- Session creation lazily creates or updates `mission_progress` only when the mission starts.

## Final status

READY FOR IMPLEMENTATION

Note: implementation must begin only after explicit approval from the review owner. The design is ready, but no production code or migration has been executed in this review phase.

## Deliverables

- `PHASE_2_4_DATABASE_DESIGN.md`
- `PHASE_2_4_API_CONTRACT.md`
- `PHASE_2_4_GAMEPLAY_LIFECYCLE.md`
- `PHASE_2_4_TEST_STRATEGY.md`
- `PHASE_2_4_FINAL_DESIGN_REVIEW.md`

## Remaining risks

- mission-specific completion logic can drift if not centralized in backend rules
- concurrent completion remains a correctness risk unless locking and uniqueness are enforced at the DB layer
- save volume may grow if game state expands beyond the MVP cap
- future mission types may require an explicit server-side config schema beyond typed metadata

## Confirmation

No production code was modified.
No database migration was created or executed.
No Docker volume was deleted or reset.
No Phase 2.3 authentication or player profile implementation was changed.

This review has not started implementation and is waiting for explicit approval before any future code work begins.

## 1. Strict instruction

Do not implement backend or frontend features yet.

Do not create or execute database migrations.

Do not modify existing Phase 2.3 authentication, account, player profile, Docker, or database configuration.

Only inspect the repository and create/update Phase 2.4 design documents.

## 2. Database design

Create a detailed ERD for:

- missions
- game_sessions
- mission_progress
- player_progress
- player_rewards
- game_saves

Also include existing accounts and player_profiles.

For every table specify:

- Column name.
- PostgreSQL data type.
- Nullable / NOT NULL.
- Primary key.
- Foreign key.
- Unique constraints.
- Indexes.
- Default values.
- Business purpose.

Explain whether player_progress is stored or calculated.

Explain how mission_progress is initialized for a new player and how newly introduced missions are handled.

Do not create unnecessary duplicated data.

## 3. Mission model

Support multiple mission types without overengineering.

Evaluate:
- QUIZ
- EXPLORATION
- STRATEGY
- STORY
- SIMULATION

Define how mission-specific configuration and completion rules are represented.

If JSONB is selected:
- Define a validation strategy.
- Define versioning.
- Reject unsupported configuration.
- Never trust client-provided completion rules.

Separate static mission definition from player-specific progress.

## 4. Game Session lifecycle

Create a state machine and transition table.

Define:
- IN_PROGRESS
- COMPLETED
- ABANDONED
- EXPIRED

Specify:
- Valid transitions.
- Invalid transitions.
- Session expiration policy.
- Duplicate completion handling.
- Concurrent completion handling.
- Reconnection/resume behavior.
- Whether multiple active sessions per player are allowed.

Provide Mermaid state diagram and sequence diagram.

## 5. Reward integrity

Design a transaction-safe reward process.

Specify:
- Reward source.
- Reward calculation.
- Idempotency key.
- Database uniqueness constraints.
- Concurrency handling.
- Rollback behavior.

A player must never receive duplicate rewards by replaying a completion request.

The backend must not trust client-submitted score, reward, mission status, or unlock state.

## 6. Game Save

Define the difference between Game Session and Game Save.

Specify:
- What gameplay state is saved.
- Save ownership.
- Save versioning.
- Maximum payload size.
- Validation.
- Save update concurrency.

Keep this feature minimal for the MVP.

## 7. API contract

For every proposed endpoint define:
- HTTP method.
- URL.
- Authentication requirement.
- Request JSON.
- Response JSON.
- HTTP status codes.
- Error codes.
- Validation rules.

Ensure account/player identity comes from the authenticated principal.

Include idempotency behavior for mission completion.

## 8. Frontend and Phaser integration

Define clear responsibilities:

React:
- Mission list.
- Mission details.
- Start session.
- Loading/error/success.
- Mission result.
- Player progress.

Phaser:
- Gameplay rendering.
- User input.
- Gameplay events.
- Session context integration.

Backend:
- Mission validation.
- Session lifecycle.
- Completion verification.
- Score and reward calculation.
- Persistent progression.

Define a TypeScript interface for the React/Phaser bridge and gameplay event payloads.

Do not let Phaser write authoritative player progress.

## 9. Testing strategy

Define concrete test cases for:

- Mission availability.
- Session ownership.
- Invalid state transitions.
- Duplicate completion.
- Concurrent completion.
- Reward integrity.
- Progress persistence.
- Save ownership.
- API authentication.
- React/Phaser integration.
- Full browser E2E on Docker Compose.

Specify which tests are unit, integration, and E2E.

## 10. Deliverables

Create:

- `PHASE_2_4_DATABASE_DESIGN.md`
- `PHASE_2_4_API_CONTRACT.md`
- `PHASE_2_4_GAMEPLAY_LIFECYCLE.md`
- `PHASE_2_4_TEST_STRATEGY.md`
- Updated `PHASE_2_4_IMPLEMENTATION_PLAN.md`

Use Mermaid ERD, state diagram, and sequence diagrams.

At the end, provide:
1. Files created/updated.
2. Architecture decisions.
3. Unresolved questions.
4. Risks.
5. Confirmation that no production code or database was changed.

Do not start implementation until the design documents have been reviewed and approved.