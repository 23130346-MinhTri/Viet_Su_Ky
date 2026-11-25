CREATE UNIQUE INDEX IF NOT EXISTS uk_game_sessions_one_active_per_player_mission
    ON app.game_sessions (player_id, mission_id)
    WHERE status = 'ACTIVE';
