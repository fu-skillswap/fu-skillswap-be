-- Phase S4: Composite partial indexes for discoverable mentor filter and recommendation sort.
-- These complement the existing individual indexes on status, is_available, average_rating.

-- Partial composite index: covers the core discoverable filter (status + is_available + verified_at NOT NULL)
-- Used by findDiscoverableCandidateIds / findDiscoverableCandidateIdsWithKeyword WHERE clauses
CREATE INDEX IF NOT EXISTS idx_mentor_profiles_discoverable
    ON mentor_profiles (status, is_available, verified_at DESC)
    WHERE status = 'ACTIVE' AND is_available = true AND verified_at IS NOT NULL;

-- Partial composite index: covers recommendation sort order (status + rating DESC + sessions DESC)
-- Used by findRecommendationCandidatesSortedByRelevance ORDER BY
CREATE INDEX IF NOT EXISTS idx_mentor_profiles_recommendation_sort
    ON mentor_profiles (status, average_rating DESC, total_completed_sessions DESC, verified_at DESC)
    WHERE status = 'ACTIVE';

-- Composite index on mentor_services: covers EXISTS(... WHERE mentor_user_id = ? AND is_active = true)
-- Note: idx_mentor_services_mentor_id already exists; this partial index reduces scan for FTS fallback
CREATE INDEX IF NOT EXISTS idx_mentor_services_active_mentor
    ON mentor_services (mentor_user_id)
    WHERE is_active = true;
