-- rollout: EXPAND
-- Course-scoped direct chat is additive; existing booking and COURSE_GROUP rows remain unchanged.
CREATE TABLE IF NOT EXISTS course_conversation_contexts (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL,
    mentee_user_id uuid NOT NULL,
    conversation_id uuid NOT NULL,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    updated_at timestamp(6) NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_conversation_context_course
        FOREIGN KEY (course_id) REFERENCES courses(id),
    CONSTRAINT fk_course_conversation_context_mentee
        FOREIGN KEY (mentee_user_id) REFERENCES users(id),
    CONSTRAINT fk_course_conversation_context_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id),
    CONSTRAINT uq_course_conversation_context_course_mentee
        UNIQUE (course_id, mentee_user_id),
    CONSTRAINT uq_course_conversation_context_conversation
        UNIQUE (conversation_id)
);

CREATE INDEX IF NOT EXISTS idx_course_conversation_context_course
    ON course_conversation_contexts (course_id);
CREATE INDEX IF NOT EXISTS idx_course_conversation_context_mentee
    ON course_conversation_contexts (mentee_user_id);
CREATE INDEX IF NOT EXISTS idx_course_conversation_context_conversation
    ON course_conversation_contexts (conversation_id);
