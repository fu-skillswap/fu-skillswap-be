-- Rollback Note:
-- Destructive: No.
-- Rollback Strategy: App rollback is possible. If needed, DB rollback can be done manually via:
-- DROP TABLE forum_comment_reactions;
-- ALTER TABLE forum_comments DROP CONSTRAINT fk_forum_comments_reply_to;
-- ALTER TABLE forum_comments DROP COLUMN reply_to_comment_id;
-- ALTER TABLE forum_comments DROP COLUMN reaction_count;
-- Affect on old data: reaction_count defaults to 0 safely. reply_to_comment_id defaults to null.

ALTER TABLE forum_comments
ADD COLUMN reaction_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE forum_comments
ADD COLUMN reply_to_comment_id UUID NULL;

ALTER TABLE forum_comments
ADD CONSTRAINT fk_forum_comments_reply_to
FOREIGN KEY (reply_to_comment_id) REFERENCES forum_comments(id);

CREATE TABLE forum_comment_reactions (
    id UUID PRIMARY KEY,
    comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(30) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    CONSTRAINT fk_forum_comment_reactions_comment FOREIGN KEY (comment_id) REFERENCES forum_comments(id),
    CONSTRAINT fk_forum_comment_reactions_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX ux_forum_comment_reactions_comment_user
ON forum_comment_reactions(comment_id, user_id);
