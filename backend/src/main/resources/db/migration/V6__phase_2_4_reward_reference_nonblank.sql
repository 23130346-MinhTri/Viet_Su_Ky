ALTER TABLE app.player_rewards
    DROP CONSTRAINT IF EXISTS player_rewards_required_source_reference_check;

ALTER TABLE app.player_rewards
    ADD CONSTRAINT player_rewards_required_source_reference_check
    CHECK (
        reward_source NOT IN ('ACHIEVEMENT', 'REPLAY', 'EVENT', 'OPTIONAL_MISSION')
        OR (
            source_reference IS NOT NULL
            AND source_reference ~ '[^[:space:]]'
        )
    );

DROP INDEX IF EXISTS app.uk_player_rewards_repeatable_reference;
