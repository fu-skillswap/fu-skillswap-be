-- rollout: EXPAND

CREATE TABLE IF NOT EXISTS google_mobile_token_replays (
    google_sub VARCHAR(255) NOT NULL,
    issued_at BIGINT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_google_mobile_token_replays PRIMARY KEY (google_sub, issued_at)
);

CREATE INDEX IF NOT EXISTS idx_google_mobile_token_replays_expires_at
    ON google_mobile_token_replays (expires_at);
