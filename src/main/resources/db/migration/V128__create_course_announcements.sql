-- rollout: EXPAND
-- Persistent course announcements are separate from realtime chat messages.
CREATE TABLE IF NOT EXISTS course_announcements (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses(id),
    author_user_id uuid NOT NULL REFERENCES users(id),
    title varchar(200) NOT NULL,
    content text NOT NULL,
    created_at timestamp(6) NOT NULL DEFAULT now(),
    updated_at timestamp(6) NOT NULL DEFAULT now(),
    published_at timestamp(6) NOT NULL DEFAULT now(),
    deleted_at timestamp(6)
);

CREATE INDEX IF NOT EXISTS idx_course_announcements_course_published
    ON course_announcements (course_id, published_at DESC);
