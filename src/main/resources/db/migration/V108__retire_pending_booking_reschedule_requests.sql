-- rollout: EXPAND
-- Reschedule is not released in production. Preserve request history for audit, but make every
-- previously actionable request terminal so no dormant PENDING workflow survives deployment.
UPDATE booking_reschedule_requests
SET status = 'EXPIRED',
    expired_at = COALESCE(expired_at, CURRENT_TIMESTAMP),
    response_note = COALESCE(response_note, 'Reschedule feature retired before request was processed.'),
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'PENDING';
