CREATE TABLE IF NOT EXISTS public_asset_upload_intents (
    id UUID PRIMARY KEY,
    owner_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    purpose VARCHAR(40) NOT NULL,
    object_key TEXT NOT NULL UNIQUE,
    expected_content_type VARCHAR(120) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    confirmed_file_id UUID UNIQUE REFERENCES files(id),
    created_at TIMESTAMP NOT NULL,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_public_asset_upload_intents_owner_expiry
    ON public_asset_upload_intents (owner_user_id, expires_at);
