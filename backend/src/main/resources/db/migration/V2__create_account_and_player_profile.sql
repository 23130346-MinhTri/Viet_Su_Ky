CREATE TABLE IF NOT EXISTS app.accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'LOCKED', 'DISABLED')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_accounts_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_accounts_email ON app.accounts (LOWER(email));

CREATE TABLE IF NOT EXISTS app.player_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL,
    display_name VARCHAR(50) NOT NULL,
    avatar_url TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_player_profiles_account UNIQUE (account_id),
    CONSTRAINT fk_player_profiles_account FOREIGN KEY (account_id) REFERENCES app.accounts (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_player_profiles_account ON app.player_profiles (account_id);

CREATE INDEX IF NOT EXISTS idx_player_profiles_display_name ON app.player_profiles (display_name);
