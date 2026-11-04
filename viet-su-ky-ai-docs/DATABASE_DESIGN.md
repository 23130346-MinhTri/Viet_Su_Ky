# Database Design — PostgreSQL

## Principles
UUID primary keys; Flyway migrations; foreign keys; unique constraints; useful timestamps; separate editorial content from player state; provenance for historical claims and questions. Use JSONB for flexible config only.

## Entity groups
**Identity:** `users`, `player_profiles`, `refresh_tokens`, optional `roles`.
**History:** `eras`, `historical_events`, `historical_figures`, `historical_locations`, `historical_topics`, `historical_sources`, `event_sources`, `figure_events`, `topic_sources`.
**Game content:** `campaigns`, `missions`, `mission_objectives`, `map_definitions`, `building_definitions`, `resource_types`, `quizzes`, `questions`, `question_options`, `question_sources`.
**Player state:** `player_missions`, `mission_objective_progress`, `quiz_attempts`, `quiz_answers`, `player_resources`, `resource_transactions`, `player_progress`, `achievements`, `player_achievements`, `reward_transactions`.
**Editorial:** `content_reviews`, optional `content_revisions`, `audit_logs`.

## Key relationships
- Era has many events and campaigns.
- Campaign has many missions; mission has many objectives.
- Quiz has many questions; question has many options and source references.
- Player has many mission states, quiz attempts, resource transactions and achievements.
- Historical events/figures/topics can cite multiple sources.

## Important fields
`users`: id, username, email, password_hash, status, created_at, updated_at.
`eras`: id, code, name, description, start_label, end_label, sort_order, publication_status.
`historical_events`: id, era_id, code, title, summary, body, time_start_label, time_end_label, certainty_level, content_classification, publication_status.
`historical_sources`: id, title, creator_or_author, publisher, publication_year, source_type, url, accessed_at, editorial_note.
`missions`: id, campaign_id, code, title, description, mission_type, difficulty, config JSONB, sort_order, publication_status.
`mission_objectives`: id, mission_id, code, objective_type, description, target_value, config JSONB.
`questions`: id, quiz_id, code, question_text, question_type, explanation, difficulty, topic_code, publication_status.
`question_options`: id, question_id, option_text, is_correct, sort_order.
`quiz_attempts`: id, user_id, quiz_id, score, total_questions, correct_answers, started_at, submitted_at.
`resource_transactions`: id, user_id, resource_type_id, amount, transaction_type, reference_type, reference_id, idempotency_key, created_at.

## Constraints
Unique email/username/content codes/achievement codes. Unique idempotency key per player. Index foreign keys and common filters. Do not represent uncertain ancient dates as exact timestamps by default; use period labels and uncertainty metadata.

## Integrity
Mission completion, reward grant and progress update must be transactional. Never trust client-supplied XP, reward, resource balance, quiz score or unlock state.

## Migration sequence (proposed)
V1 identity; V2 history; V3 campaign/mission; V4 quiz; V5 progress; V6 resources/rewards; V7 editorial/audit. Adapt to existing migrations; never rewrite an applied migration.
