ALTER TABLE app.missions
    DROP CONSTRAINT IF EXISTS missions_mission_type_check;

UPDATE app.missions
SET mission_type = CASE
    WHEN mission_type = 'QUEST' THEN 'QUIZ'
    WHEN mission_type = 'CHALLENGE' THEN 'STRATEGY'
    WHEN mission_type = 'SCENARIO' THEN 'STORY'
    WHEN mission_type = 'BOSS' THEN 'SIMULATION'
    ELSE mission_type
END
WHERE mission_type IN ('QUEST', 'CHALLENGE', 'SCENARIO', 'BOSS');

ALTER TABLE app.missions
    ADD CONSTRAINT missions_mission_type_check CHECK (mission_type IN ('QUIZ', 'EXPLORATION', 'STRATEGY', 'STORY', 'SIMULATION'));

ALTER TABLE app.mission_progress
    DROP CONSTRAINT IF EXISTS mission_progress_status_check;

ALTER TABLE app.mission_progress
    ADD CONSTRAINT mission_progress_status_check CHECK (status IN ('AVAILABLE', 'LOCKED', 'IN_PROGRESS', 'COMPLETED'));

ALTER TABLE app.player_rewards
    ADD COLUMN IF NOT EXISTS session_id UUID,
    ADD COLUMN IF NOT EXISTS reward_source VARCHAR(50) NOT NULL DEFAULT 'MISSION_COMPLETION',
    ADD COLUMN IF NOT EXISTS source_reference VARCHAR(128);

ALTER TABLE app.player_rewards
    DROP CONSTRAINT IF EXISTS uk_player_rewards_player_mission;

ALTER TABLE app.player_rewards
    ADD CONSTRAINT player_rewards_reward_source_check CHECK (reward_source IN ('MISSION_COMPLETION', 'ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION'));

ALTER TABLE app.player_rewards
    ADD CONSTRAINT fk_player_rewards_session FOREIGN KEY (session_id) REFERENCES app.game_sessions (id) ON DELETE SET NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_player_rewards_mission_reward
    ON app.player_rewards (player_id, mission_id, reward_source, reward_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_player_rewards_repeatable
    ON app.player_rewards (player_id, reward_source, source_reference, reward_type);

CREATE UNIQUE INDEX IF NOT EXISTS uk_player_rewards_session_reward
    ON app.player_rewards (player_id, session_id, reward_source, reward_type);

ALTER TABLE app.game_sessions
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 0;

ALTER TABLE app.game_saves
    ADD COLUMN IF NOT EXISTS version INTEGER NOT NULL DEFAULT 1;

CREATE TABLE IF NOT EXISTS app.idempotency_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    mission_id UUID,
    session_id UUID,
    operation_name VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash TEXT NOT NULL,
    response_json TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SUCCESS', 'REJECTED', 'CONFLICT')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT uk_idempotency_records_operation UNIQUE (operation_name, idempotency_key),
    CONSTRAINT uk_idempotency_records_session UNIQUE (player_id, session_id, operation_name),
    CONSTRAINT fk_idempotency_records_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_idempotency_records_mission FOREIGN KEY (mission_id) REFERENCES app.missions (id) ON DELETE SET NULL,
    CONSTRAINT fk_idempotency_records_session FOREIGN KEY (session_id) REFERENCES app.game_sessions (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_idempotency_records_player_operation ON app.idempotency_records (player_id, operation_name, created_at DESC);
