ALTER TABLE app.player_rewards
    ADD CONSTRAINT player_rewards_required_source_reference_check
    CHECK (
        reward_source NOT IN ('ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION')
        OR source_reference IS NOT NULL
    );

CREATE UNIQUE INDEX IF NOT EXISTS uk_player_rewards_repeatable_reference
    ON app.player_rewards (player_id, reward_source, source_reference, reward_type)
    WHERE reward_source IN ('ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION')
      AND source_reference IS NOT NULL;
