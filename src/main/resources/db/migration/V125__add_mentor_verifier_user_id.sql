-- rollout: EXPAND
-- Preserve the verifier relationship represented by MentorProfile.verifiedByUserId.

ALTER TABLE mentor_profiles
    ADD COLUMN IF NOT EXISTS verified_by_user_id UUID;

ALTER TABLE mentor_profiles
    ADD CONSTRAINT fk_mentor_profiles_verified_by_user
    FOREIGN KEY (verified_by_user_id) REFERENCES users(id);
