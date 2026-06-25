UPDATE bookings
SET selected_start_time = COALESCE(selected_start_time, requested_start_time),
    selected_end_time = COALESCE(selected_end_time, requested_end_time)
WHERE selected_start_time IS NULL
   OR selected_end_time IS NULL;

UPDATE bookings
SET requested_start_time = COALESCE(requested_start_time, selected_start_time),
    requested_end_time = COALESCE(requested_end_time, selected_end_time)
WHERE requested_start_time IS NULL
   OR requested_end_time IS NULL;

ALTER TABLE bookings
    ALTER COLUMN requested_start_time DROP NOT NULL,
    ALTER COLUMN requested_end_time DROP NOT NULL;
