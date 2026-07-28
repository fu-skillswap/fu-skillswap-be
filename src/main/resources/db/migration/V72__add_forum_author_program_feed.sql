-- rollout: EXPAND
-- Snapshot the author's academic program for program-prioritized Forum feeds.
ALTER TABLE forum_posts
    ADD COLUMN author_program_id UUID;

ALTER TABLE forum_posts
    ADD CONSTRAINT fk_forum_posts_author_program
    FOREIGN KEY (author_program_id) REFERENCES academic_programs(id);

-- Existing posts inherit the program currently associated with their author.
UPDATE forum_posts post
SET author_program_id = profile.program_id
FROM student_profiles profile
WHERE post.author_user_id = profile.user_id
  AND post.author_program_id IS NULL
  AND profile.program_id IS NOT NULL;

CREATE INDEX idx_forum_posts_status_program_activity_id
    ON forum_posts (status, author_program_id, last_activity_at DESC, id DESC);
