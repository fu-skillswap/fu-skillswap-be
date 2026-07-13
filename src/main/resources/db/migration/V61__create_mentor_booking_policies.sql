CREATE TABLE IF NOT EXISTS mentor_booking_policies (
    mentor_user_id uuid NOT NULL,
    minimum_booking_lead_time_minutes integer NOT NULL DEFAULT 120,
    maximum_booking_horizon_days integer NOT NULL DEFAULT 30,
    timezone varchar(64) NOT NULL DEFAULT 'Asia/Ho_Chi_Minh',
    created_at timestamp(6) NOT NULL,
    updated_at timestamp(6) NOT NULL,
    CONSTRAINT pk_mentor_booking_policies PRIMARY KEY (mentor_user_id),
    CONSTRAINT fk_mentor_booking_policies_mentor FOREIGN KEY (mentor_user_id) REFERENCES users (id) ON DELETE CASCADE
);
