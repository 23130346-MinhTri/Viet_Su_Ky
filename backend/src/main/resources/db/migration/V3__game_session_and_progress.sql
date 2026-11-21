CREATE TABLE IF NOT EXISTS app.missions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL,
    title VARCHAR(150) NOT NULL,
    description TEXT,
    chapter VARCHAR(100) NOT NULL DEFAULT 'CHAPTER_1',
    mission_order INTEGER NOT NULL,
    mission_type VARCHAR(30) NOT NULL DEFAULT 'QUEST' CHECK (mission_type IN ('QUEST', 'CHALLENGE', 'SCENARIO', 'BOSS')),
    unlock_condition VARCHAR(255),
    prerequisite_mission_id UUID,
    reward_points INTEGER NOT NULL DEFAULT 0,
    reward_code VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_missions_code UNIQUE (code),
    CONSTRAINT fk_missions_prerequisite FOREIGN KEY (prerequisite_mission_id) REFERENCES app.missions (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_missions_status_order ON app.missions (status, mission_order);
CREATE INDEX IF NOT EXISTS idx_missions_chapter ON app.missions (chapter, mission_order);

CREATE TABLE IF NOT EXISTS app.game_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED', 'EXPIRED')),
    event_count INTEGER NOT NULL DEFAULT 0,
    final_score INTEGER NOT NULL DEFAULT 0,
    metadata TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_game_sessions_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_game_sessions_mission FOREIGN KEY (mission_id) REFERENCES app.missions (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_game_sessions_player_status ON app.game_sessions (player_id, status, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_game_sessions_mission ON app.game_sessions (mission_id, status);

CREATE TABLE IF NOT EXISTS app.mission_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('LOCKED', 'AVAILABLE', 'IN_PROGRESS', 'COMPLETED')),
    progress_value INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_event_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_mission_progress_player_mission UNIQUE (player_id, mission_id),
    CONSTRAINT fk_mission_progress_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_progress_mission FOREIGN KEY (mission_id) REFERENCES app.missions (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mission_progress_player ON app.mission_progress (player_id, status);
CREATE INDEX IF NOT EXISTS idx_mission_progress_mission ON app.mission_progress (mission_id, status);

CREATE TABLE IF NOT EXISTS app.player_progress (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL UNIQUE,
    chapter VARCHAR(100) NOT NULL DEFAULT 'CHAPTER_1',
    total_missions_completed INTEGER NOT NULL DEFAULT 0,
    total_points INTEGER NOT NULL DEFAULT 0,
    unlocked_content TEXT NOT NULL DEFAULT '[]',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_player_progress_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_player_progress_points ON app.player_progress (player_id, total_points);

CREATE TABLE IF NOT EXISTS app.player_rewards (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    reward_type VARCHAR(30) NOT NULL DEFAULT 'MISSION_COMPLETE' CHECK (reward_type IN ('MISSION_COMPLETE', 'EVENT_REWARD', 'DAILY_BONUS')),
    reward_points INTEGER NOT NULL DEFAULT 0,
    reward_code VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'CLAIMED' CHECK (status IN ('CLAIMED', 'REDEEMED')),
    claimed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_player_rewards_player_mission UNIQUE (player_id, mission_id, reward_type),
    CONSTRAINT fk_player_rewards_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_player_rewards_mission FOREIGN KEY (mission_id) REFERENCES app.missions (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS idx_player_rewards_player ON app.player_rewards (player_id, claimed_at DESC);

CREATE TABLE IF NOT EXISTS app.game_saves (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    player_id UUID NOT NULL,
    mission_id UUID,
    session_id UUID,
    save_name VARCHAR(100) NOT NULL,
    save_data TEXT NOT NULL,
    save_size INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_game_saves_player_name UNIQUE (player_id, save_name),
    CONSTRAINT fk_game_saves_player FOREIGN KEY (player_id) REFERENCES app.player_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_game_saves_mission FOREIGN KEY (mission_id) REFERENCES app.missions (id) ON DELETE SET NULL,
    CONSTRAINT fk_game_saves_session FOREIGN KEY (session_id) REFERENCES app.game_sessions (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_game_saves_player ON app.game_saves (player_id, updated_at DESC);
