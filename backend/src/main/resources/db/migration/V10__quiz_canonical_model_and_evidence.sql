DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'uk_game_sessions_id_mission'
          AND conrelid = 'app.game_sessions'::regclass
    ) THEN
        ALTER TABLE app.game_sessions
            ADD CONSTRAINT uk_game_sessions_id_mission UNIQUE (id, mission_id);
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS app.quiz_questions (
    id UUID NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    prompt TEXT NOT NULL,
    explanation TEXT,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, version)
);

CREATE TABLE IF NOT EXISTS app.quiz_answer_options (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    question_version INTEGER NOT NULL,
    option_key VARCHAR(20) NOT NULL,
    option_text TEXT NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order BETWEEN 1 AND 4),
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quiz_answer_options_question_key UNIQUE (question_id, question_version, option_key),
    CONSTRAINT uk_quiz_answer_options_question_order UNIQUE (question_id, question_version, display_order),
    CONSTRAINT uk_quiz_answer_options_exact UNIQUE (id, question_id, question_version),
    CONSTRAINT fk_quiz_answer_options_question
        FOREIGN KEY (question_id, question_version)
        REFERENCES app.quiz_questions (id, version)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_quiz_answer_options_single_correct
    ON app.quiz_answer_options (question_id, question_version)
    WHERE is_correct = TRUE;

CREATE TABLE IF NOT EXISTS app.quiz_mission_questions (
    mission_id UUID NOT NULL,
    question_id UUID NOT NULL,
    question_version INTEGER NOT NULL,
    question_order INTEGER NOT NULL CHECK (question_order BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mission_id, question_order),
    CONSTRAINT uk_quiz_mission_questions_question UNIQUE (mission_id, question_id, question_version),
    CONSTRAINT uk_quiz_mission_questions_exact UNIQUE (mission_id, question_id, question_version, question_order),
    CONSTRAINT fk_quiz_mission_questions_mission
        FOREIGN KEY (mission_id) REFERENCES app.missions (id),
    CONSTRAINT fk_quiz_mission_questions_question
        FOREIGN KEY (question_id, question_version)
        REFERENCES app.quiz_questions (id, version)
);

CREATE TABLE IF NOT EXISTS app.quiz_session_questions (
    session_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    question_id UUID NOT NULL,
    question_version INTEGER NOT NULL,
    question_order INTEGER NOT NULL CHECK (question_order BETWEEN 1 AND 5),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, question_order),
    CONSTRAINT uk_quiz_session_questions_question UNIQUE (session_id, question_id, question_version),
    CONSTRAINT uk_quiz_session_questions_exact UNIQUE (session_id, mission_id, question_id, question_version, question_order),
    CONSTRAINT fk_quiz_session_questions_session
        FOREIGN KEY (session_id, mission_id)
        REFERENCES app.game_sessions (id, mission_id),
    CONSTRAINT fk_quiz_session_questions_mission_question
        FOREIGN KEY (mission_id, question_id, question_version, question_order)
        REFERENCES app.quiz_mission_questions (mission_id, question_id, question_version, question_order)
);

CREATE TABLE IF NOT EXISTS app.quiz_answer_evidence (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    mission_id UUID NOT NULL,
    question_id UUID NOT NULL,
    question_version INTEGER NOT NULL,
    question_order INTEGER NOT NULL CHECK (question_order BETWEEN 1 AND 5),
    selected_option_id UUID NOT NULL,
    event_id UUID NOT NULL,
    request_hash TEXT NOT NULL,
    response_json TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    points INTEGER NOT NULL CHECK (points IN (0,20)),
    recorded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quiz_answer_evidence_question UNIQUE (session_id, question_id, question_version),
    CONSTRAINT uk_quiz_answer_evidence_order UNIQUE (session_id, question_order),
    CONSTRAINT uk_quiz_answer_evidence_event UNIQUE (session_id, event_id),
    CONSTRAINT uk_quiz_answer_evidence_exact UNIQUE (session_id, mission_id, question_id, question_version, question_order),
    CONSTRAINT fk_quiz_answer_evidence_session
        FOREIGN KEY (session_id) REFERENCES app.game_sessions (id),
    CONSTRAINT fk_quiz_answer_evidence_snapshot
        FOREIGN KEY (session_id, mission_id, question_id, question_version, question_order)
        REFERENCES app.quiz_session_questions (session_id, mission_id, question_id, question_version, question_order),
    CONSTRAINT fk_quiz_answer_evidence_option
        FOREIGN KEY (selected_option_id, question_id, question_version)
        REFERENCES app.quiz_answer_options (id, question_id, question_version),
    CONSTRAINT ck_quiz_answer_evidence_score
        CHECK ((is_correct = TRUE AND points = 20) OR (is_correct = FALSE AND points = 0))
);

ALTER TABLE app.quiz_answer_evidence
    ALTER COLUMN question_version TYPE INTEGER USING question_version::INTEGER,
    ALTER COLUMN question_order TYPE INTEGER USING question_order::INTEGER,
    ALTER COLUMN points TYPE INTEGER USING points::INTEGER;

CREATE INDEX IF NOT EXISTS idx_quiz_answer_evidence_session
    ON app.quiz_answer_evidence (session_id, question_order);

CREATE OR REPLACE FUNCTION app.quiz_validate_question_publication()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.active = TRUE THEN
        IF (SELECT COUNT(*) FROM app.quiz_answer_options
            WHERE question_id = NEW.id AND question_version = NEW.version) < 2 THEN
            RAISE EXCEPTION 'A published quiz question must have at least two answer options';
        END IF;
        IF (SELECT COUNT(*) FROM app.quiz_answer_options
            WHERE question_id = NEW.id AND question_version = NEW.version AND is_correct = TRUE) <> 1 THEN
            RAISE EXCEPTION 'A published quiz question must have exactly one correct option';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_validate_question_publication ON app.quiz_questions;
CREATE TRIGGER trg_quiz_validate_question_publication
BEFORE INSERT OR UPDATE OF active ON app.quiz_questions
FOR EACH ROW EXECUTE FUNCTION app.quiz_validate_question_publication();

CREATE OR REPLACE FUNCTION app.quiz_prevent_answer_evidence_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Quiz answer evidence is immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_answer_evidence_no_update ON app.quiz_answer_evidence;
CREATE TRIGGER trg_quiz_answer_evidence_no_update
BEFORE UPDATE OR DELETE ON app.quiz_answer_evidence
FOR EACH ROW EXECUTE FUNCTION app.quiz_prevent_answer_evidence_mutation();

CREATE OR REPLACE FUNCTION app.quiz_prevent_published_question_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.active = TRUE THEN
        RAISE EXCEPTION 'Published quiz question versions are immutable';
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_question_immutable_after_publication ON app.quiz_questions;
CREATE TRIGGER trg_quiz_question_immutable_after_publication
BEFORE UPDATE OR DELETE ON app.quiz_questions
FOR EACH ROW EXECUTE FUNCTION app.quiz_prevent_published_question_mutation();

CREATE OR REPLACE FUNCTION app.quiz_prevent_published_option_mutation()
RETURNS TRIGGER AS $$
DECLARE
    target_question_id UUID;
    target_question_version INTEGER;
    question_is_active BOOLEAN;
BEGIN
    IF TG_OP IN ('UPDATE', 'DELETE') THEN
        target_question_id := OLD.question_id;
        target_question_version := OLD.question_version;
        SELECT active INTO question_is_active
        FROM app.quiz_questions
        WHERE id = target_question_id AND version = target_question_version;
        IF question_is_active = TRUE THEN
            RAISE EXCEPTION 'Answer options for a published quiz question are immutable';
        END IF;
    END IF;

    IF TG_OP IN ('INSERT', 'UPDATE') THEN
        target_question_id := NEW.question_id;
        target_question_version := NEW.question_version;
        SELECT active INTO question_is_active
        FROM app.quiz_questions
        WHERE id = target_question_id AND version = target_question_version;
        IF question_is_active = TRUE THEN
            RAISE EXCEPTION 'Answer options for a published quiz question are immutable';
        END IF;
    END IF;
    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_answer_option_immutable_after_publication ON app.quiz_answer_options;
CREATE TRIGGER trg_quiz_answer_option_immutable_after_publication
BEFORE INSERT OR UPDATE OR DELETE ON app.quiz_answer_options
FOR EACH ROW EXECUTE FUNCTION app.quiz_prevent_published_option_mutation();

CREATE OR REPLACE FUNCTION app.quiz_validate_answer_evidence()
RETURNS TRIGGER AS $$
DECLARE
    selected_option_is_correct BOOLEAN;
BEGIN
    SELECT is_correct INTO selected_option_is_correct
    FROM app.quiz_answer_options
    WHERE id = NEW.selected_option_id
      AND question_id = NEW.question_id
      AND question_version = NEW.question_version;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Selected option does not belong to this quiz question version';
    END IF;
    IF NEW.is_correct IS DISTINCT FROM selected_option_is_correct THEN
        RAISE EXCEPTION 'Quiz answer evidence correctness does not match the selected canonical option';
    END IF;
    IF (selected_option_is_correct AND NEW.points <> 20)
       OR (NOT selected_option_is_correct AND NEW.points <> 0) THEN
        RAISE EXCEPTION 'Quiz answer evidence does not match the selected canonical option';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_answer_evidence_validate ON app.quiz_answer_evidence;
CREATE TRIGGER trg_quiz_answer_evidence_validate
BEFORE INSERT ON app.quiz_answer_evidence
FOR EACH ROW EXECUTE FUNCTION app.quiz_validate_answer_evidence();

CREATE OR REPLACE FUNCTION app.quiz_prevent_session_snapshot_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Quiz session question snapshots are immutable';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_session_question_no_update ON app.quiz_session_questions;
CREATE TRIGGER trg_quiz_session_question_no_update
BEFORE UPDATE OR DELETE ON app.quiz_session_questions
FOR EACH ROW EXECUTE FUNCTION app.quiz_prevent_session_snapshot_mutation();

CREATE UNIQUE INDEX IF NOT EXISTS uk_quiz_mission_questions_logical_question
    ON app.quiz_mission_questions (mission_id, question_id);

CREATE OR REPLACE FUNCTION app.quiz_validate_session_question_set()
RETURNS TRIGGER AS $$
DECLARE
    mission_type VARCHAR(30);
    mapped_count BIGINT;
BEGIN
    SELECT m.mission_type INTO mission_type
    FROM app.missions m
    WHERE m.id = NEW.mission_id;

    IF mission_type = 'QUIZ' THEN
        SELECT COUNT(*) INTO mapped_count
        FROM app.quiz_mission_questions mq
        JOIN app.quiz_questions q
          ON q.id = mq.question_id
         AND q.version = mq.question_version
        WHERE mq.mission_id = NEW.mission_id
          AND q.active = TRUE;

        IF mapped_count <> 5 THEN
            RAISE EXCEPTION 'A Quiz session requires exactly five published mission questions';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_quiz_session_requires_five_published_questions ON app.game_sessions;
CREATE TRIGGER trg_quiz_session_requires_five_published_questions
BEFORE INSERT ON app.game_sessions
FOR EACH ROW EXECUTE FUNCTION app.quiz_validate_session_question_set();
