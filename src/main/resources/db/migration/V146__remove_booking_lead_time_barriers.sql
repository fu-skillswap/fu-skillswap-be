-- rollout: EXPAND

UPDATE mentor_booking_policies
SET minimum_booking_lead_time_minutes = 0
WHERE minimum_booking_lead_time_minutes <> 0;

ALTER TABLE mentor_booking_policies
    ALTER COLUMN minimum_booking_lead_time_minutes SET DEFAULT 0;
