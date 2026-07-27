-- EXPAND: admin-managed phrase rules for Forum write-time moderation.
CREATE TABLE forum_prohibited_phrases (
    id UUID NOT NULL,
    phrase VARCHAR(200) NOT NULL,
    normalized_phrase VARCHAR(200) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_user_id UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_forum_prohibited_phrases PRIMARY KEY (id),
    CONSTRAINT uq_forum_prohibited_phrases_normalized_phrase UNIQUE (normalized_phrase),
    CONSTRAINT fk_forum_prohibited_phrases_creator
        FOREIGN KEY (created_by_user_id) REFERENCES users(id)
);

CREATE INDEX idx_forum_prohibited_phrases_active_created
    ON forum_prohibited_phrases (is_active, created_at DESC, id DESC);
