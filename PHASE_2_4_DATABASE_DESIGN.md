# PHASE 2.4 DATABASE DESIGN

## Scope and design intent

This design aligns with the repository’s solved domain structure: `accounts` and `player_profiles` remain the identity root while `missions`, `game_sessions`, `mission_progress`, `player_progress`, `player_rewards`, and `game_saves` model progression and persistence. The accepted final design keeps the schema minimal and avoids duplicating any fact that can be derived from authoritative source tables.

This document resolves the final accepted enum set and idempotency/reward design before implementation.

## Final enum definitions

GameSessionStatus:
- `ACTIVE`
- `COMPLETED`
- `ABANDONED`
- `EXPIRED`

MissionProgressStatus:
- `AVAILABLE`
- `LOCKED`
- `IN_PROGRESS`
- `COMPLETED`

MissionType:
- `QUIZ`
- `EXPLORATION`
- `STRATEGY`
- `STORY`
- `SIMULATION`

Important note:
- The repository currently contains older values such as `QUEST`/`CHALLENGE`/`SCENARIO`/`BOSS` in the existing Phase 2.4 code and migration snapshot; those are treated as existing implementation artifacts and are not modified during this design review. The final accepted design resolves them to the values above for future implementation.

## Data model overview

- `accounts`: authenticated identity
- `player_profiles`: one profile per account; player domain root
- `missions`: static mission catalog
- `game_sessions`: runtime play session for a player + mission
- `mission_progress`: per-player/per-mission progress record
- `player_progress`: derived aggregate summary for player
- `player_rewards`: immutable reward ledger
- `game_saves`: resumable save snapshots
- `idempotency_records`: request deduplication ledger for completion requests

## ERD

```mermaid
erDiagram
    accounts ||--|| player_profiles : owns
    player_profiles ||--o{ game_sessions : plays
    player_profiles ||--o{ mission_progress : tracks
    player_profiles ||--|| player_progress : summarizes
    player_profiles ||--o{ player_rewards : receives
    player_profiles ||--o{ game_saves : stores
    player_profiles ||--o{ idempotency_records : uses
    missions ||--o{ game_sessions : assigned_to
    missions ||--o{ mission_progress : defines
    missions ||--o{ player_rewards : rewards
    missions ||--o{ game_saves : references
    missions ||--o{ missions : prerequisite

    accounts {
        UUID id PK
        VARCHAR email
        VARCHAR password_hash
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    player_profiles {
        UUID id PK
        UUID account_id FK
        VARCHAR display_name
        VARCHAR avatar_url
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    missions {
        UUID id PK
        VARCHAR code UK
        VARCHAR title
        VARCHAR description
        VARCHAR chapter
        INTEGER mission_order
        VARCHAR mission_type
        VARCHAR unlock_condition
        UUID prerequisite_mission_id FK
        INTEGER reward_points
        VARCHAR reward_code
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    game_sessions {
        UUID id PK
        UUID player_id FK
        UUID mission_id FK
        TIMESTAMP started_at
        TIMESTAMP ended_at
        VARCHAR status
        INTEGER event_count
        INTEGER final_score
        TEXT metadata
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    mission_progress {
        UUID id PK
        UUID player_id FK
        UUID mission_id FK
        VARCHAR status
        INTEGER progress_value
        TIMESTAMP started_at
        TIMESTAMP completed_at
        TIMESTAMP last_event_at
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    player_progress {
        UUID id PK
        UUID player_id FK UK
        VARCHAR chapter
        INTEGER total_missions_completed
        INTEGER total_points
        TEXT unlocked_content
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    player_rewards {
        UUID id PK
        UUID player_id FK
        UUID mission_id FK
        VARCHAR reward_source
        VARCHAR reward_type
        INTEGER reward_points
        VARCHAR reward_code
        VARCHAR status
        TIMESTAMP claimed_at
        TIMESTAMP created_at
    }

    game_saves {
        UUID id PK
        UUID player_id FK
        UUID mission_id FK
        UUID session_id FK
        VARCHAR save_name
        TEXT save_data
        INTEGER save_size
        INTEGER version
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    idempotency_records {
        UUID id PK
        UUID player_id FK
        UUID mission_id FK
        UUID session_id FK
        VARCHAR operation_name
        VARCHAR idempotency_key
        TEXT request_hash
        TEXT response_json
        VARCHAR status
        TIMESTAMP created_at
        TIMESTAMP completed_at
    }
```

## Table design

### `app.missions`

Purpose: static mission definition; no player-specific state.

Columns:
- `id` UUID PK, default `gen_random_uuid()`
- `code` VARCHAR(100) NOT NULL UNIQUE
- `title` VARCHAR(150) NOT NULL
- `description` TEXT NULL
- `chapter` VARCHAR(100) NOT NULL DEFAULT `CHAPTER_1`
- `mission_order` INTEGER NOT NULL
- `mission_type` VARCHAR(30) NOT NULL DEFAULT `QUIZ` CHECK in (`QUIZ`, `EXPLORATION`, `STRATEGY`, `STORY`, `SIMULATION`)
- `unlock_condition` VARCHAR(255) NULL
- `prerequisite_mission_id` UUID NULL FK -> `missions.id`
- `reward_points` INTEGER NOT NULL DEFAULT 0
- `reward_code` VARCHAR(100) NULL
- `status` VARCHAR(20) NOT NULL DEFAULT `ACTIVE` CHECK in (`DRAFT`, `ACTIVE`, `ARCHIVED`)
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes/constraints:
- unique `(code)`
- index `(status, mission_order)`
- index `(chapter, mission_order)`

### `app.game_sessions`

Purpose: one runtime mission instance for a player.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL FK -> `player_profiles.id`
- `mission_id` UUID NOT NULL FK -> `missions.id`
- `started_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `ended_at` TIMESTAMP NULL
- `status` VARCHAR(30) NOT NULL DEFAULT `ACTIVE` CHECK in (`ACTIVE`, `COMPLETED`, `ABANDONED`, `EXPIRED`)
- `event_count` INTEGER NOT NULL DEFAULT 0
- `final_score` INTEGER NOT NULL DEFAULT 0
- `metadata` TEXT NULL
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Indexes/constraints:
- index `(player_id, status, started_at DESC)`
- index `(mission_id, status)`
- foreign keys to `player_profiles` and `missions`

### `app.mission_progress`

Purpose: canonical per-player progress for each mission.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL FK -> `player_profiles.id`
- `mission_id` UUID NOT NULL FK -> `missions.id`
- `status` VARCHAR(30) NOT NULL DEFAULT `AVAILABLE` CHECK in (`AVAILABLE`, `LOCKED`, `IN_PROGRESS`, `COMPLETED`)
- `progress_value` INTEGER NOT NULL DEFAULT 0
- `started_at` TIMESTAMP NULL
- `completed_at` TIMESTAMP NULL
- `last_event_at` TIMESTAMP NULL
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Constraints:
- unique `(player_id, mission_id)`
- index `(player_id, status)`
- index `(mission_id, status)`

### `app.player_progress`

Purpose: derived, denormalized aggregate summary.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL UNIQUE FK -> `player_profiles.id`
- `chapter` VARCHAR(100) NOT NULL DEFAULT `CHAPTER_1`
- `total_missions_completed` INTEGER NOT NULL DEFAULT 0
- `total_points` INTEGER NOT NULL DEFAULT 0
- `unlocked_content` TEXT NOT NULL DEFAULT `'[]'`
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

### `app.player_rewards`

Purpose: immutable reward ledger; source of truth for reward issuance.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL FK -> `player_profiles.id`
- `mission_id` UUID NOT NULL FK -> `missions.id`
- `session_id` UUID NULL FK -> `game_sessions.id`
- `reward_source` VARCHAR(50) NOT NULL DEFAULT `MISSION_COMPLETION` CHECK in (`MISSION_COMPLETION`, `ACHIEVEMENT`, `REPLAY`, `EVENT`, `OPTIONAL_MISSION`)
- `source_reference` VARCHAR(128) NULL
- `reward_type` VARCHAR(50) NOT NULL DEFAULT `POINTS` CHECK in (`POINTS`, `BADGE`, `ITEM`, `UNLOCK`, `CURRENCY`)
- `reward_points` INTEGER NOT NULL DEFAULT 0
- `reward_code` VARCHAR(100) NULL
- `status` VARCHAR(20) NOT NULL DEFAULT `CLAIMED` CHECK in (`CLAIMED`, `REDEEMED`)
- `claimed_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Constraints:
- unique `(player_id, mission_id, reward_source, reward_type)` for one-time mission completion rewards
- unique `(player_id, reward_source, source_reference, reward_type)` for repeatable rewards keyed by a specific event or replay occurrence
- unique `(player_id, session_id, reward_source, reward_type)` for session-scoped one-time grant patterns
- index `(player_id, claimed_at DESC)`
- index `(player_id, reward_source, source_reference)`

Design rule:
- `source_reference` stores a stable business identifier such as an achievement code, replay label, event id, or optional mission key.
- `session_id` is optional and helps to tie the reward to a specific mission/playthrough when that is useful.
- the database supports both one-time and repeatable grant patterns without forcing all rewards into a single uniqueness model.

### `app.game_saves`

Purpose: resumable, non-authoritative gameplay snapshot data.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL FK -> `player_profiles.id`
- `mission_id` UUID NULL FK -> `missions.id`
- `session_id` UUID NULL FK -> `game_sessions.id`
- `save_name` VARCHAR(100) NOT NULL
- `save_data` TEXT NOT NULL
- `save_size` INTEGER NOT NULL DEFAULT 0
- `version` INTEGER NOT NULL DEFAULT 1
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP

Constraints:
- unique `(player_id, save_name)`
- index `(player_id, updated_at DESC)`

### `app.idempotency_records`

Purpose: safe deduplication of mission completion and reward requests.

Columns:
- `id` UUID PK
- `player_id` UUID NOT NULL FK -> `player_profiles.id`
- `mission_id` UUID NULL FK -> `missions.id`
- `session_id` UUID NULL FK -> `game_sessions.id`
- `operation_name` VARCHAR(80) NOT NULL
- `idempotency_key` VARCHAR(255) NOT NULL
- `request_hash` TEXT NOT NULL
- `response_json` TEXT NULL
- `status` VARCHAR(20) NOT NULL DEFAULT `PENDING` CHECK in (`PENDING`, `SUCCESS`, `REJECTED`, `CONFLICT`)
- `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
- `completed_at` TIMESTAMP NULL

Constraints:
- unique `(operation_name, idempotency_key)`
- unique `(player_id, session_id, operation_name)` for one completion transaction per session
- index `(player_id, operation_name, created_at DESC)`

Idempotency semantics:
- same key + same request => return stored response
- same key + different request => reject with `409 Conflict`
- different key + already completed session => reject with `409 Conflict`
- validation failure does not create a successful idempotency record; the request may be persisted as `REJECTED` if the API chooses to track rejected attempts, but it must not be used as a successful completion record
- system-level retryable failures may be retried using the same idempotency key after the transaction is rolled back; they must not generate duplicate reward rows or success states

## Stored vs calculated progress

`player_progress` is stored but derived.

Rules:
- `mission_progress` is the authoritative source for mission availability, progress, and completion.
- `player_rewards` is the authoritative source for reward issuance.
- `player_progress` is recalculated whenever a session succeeds or a reward is issued, then persisted for fast reads.
- client-supplied progress totals are never trusted.

## Mission progress initialization

For a new player:
- create a `mission_progress` row only when a mission is first queried or started
- default status resolves as:
  - `LOCKED` if a prerequisite exists and its status is not `COMPLETED`
  - otherwise `AVAILABLE`
- `progress_value = 0`
- `started_at = NULL`, `completed_at = NULL`, `last_event_at = NULL`

For an existing player and a newly published mission:
- resolve mission availability by evaluating prerequisite completion
- if mission has no progress row and prerequisites are satisfied, create a row lazily with status `AVAILABLE`
- if prerequisites are unmet, keep it `LOCKED` without creating unnecessary progress rows for missions the player is not actively interacting with

Avoid unnecessary per-player, per-mission rows for all missions on player creation. The database should derive availability on read, not pre-populate every row.

## Reward integrity and uniqueness

Reward issuance design must use both application-level validation and database uniqueness.

Recommended strategy:
- `player_rewards` unique constraint on `(player_id, mission_id, reward_source, reward_type)` blocks duplicate issuance even under concurrent requests.
- `idempotency_records` protects repeated execution of the same completion request using the same idempotency key.
- A completion transaction should:
  1. lock the `game_sessions` row or equivalent optimistic version
  2. validate mission ownership and rules
  3. calculate server-side final score and reward amount
  4. insert `idempotency_records` or confirm existing record
  5. update mission/session/progress rows
  6. insert reward row only if not already present
  7. recalculate `player_progress`

One-time rewards:
- first completion reward
- achievement reward
- optional mission reward if only one reward per mission is allowed

Repeatable rewards:
- replay rewards
- event rewards
- any reward with the same mission but different `reward_source` values

The reward source/ref must distinguish these scenarios and avoid accidental collision.

## Game Save versioning and ownership

- `game_saves.version` is required for optimistic locking.
- `PUT /api/v1/players/me/game-saves/{saveId}` is interpreted as update-only for an existing save.
- create endpoint may be `POST /api/v1/players/me/game-saves` if a save slot is new.
- a version mismatch returns `409 CONFLICT` or `412 PRECONDITION_FAILED` and must not overwrite the latest state.
- all save ownership checks are based on authenticated principal + `player_id` relationship.

## Final design status

This is a design-only revision. No production code, migration, or database state was changed during this review.
