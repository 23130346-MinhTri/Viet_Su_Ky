ALTER TABLE app.game_sessions
    ADD COLUMN IF NOT EXISTS last_activity_at TIMESTAMP;

UPDATE app.game_sessions
SET last_activity_at = started_at
WHERE last_activity_at IS NULL;

ALTER TABLE app.game_sessions
    ALTER COLUMN last_activity_at SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_game_sessions_active_expiration
    ON app.game_sessions (last_activity_at, started_at)
    WHERE status = 'ACTIVE';
