-- rollout: EXPAND
-- Align the mentor verification request actor mappings with the released JPA model.

ALTER TABLE mentor_verification_requests
    ADD COLUMN IF NOT EXISTS locked_by_user_id UUID,
    ADD COLUMN IF NOT EXISTS reviewed_by_user_id UUID;

ALTER TABLE mentor_verification_requests
    ADD CONSTRAINT fk_mentor_verification_locked_by_user
        FOREIGN KEY (locked_by_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_mentor_verification_reviewer_user
        FOREIGN KEY (reviewed_by_user_id) REFERENCES users(id);
