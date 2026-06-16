CREATE TABLE IF NOT EXISTS booking_reschedule_requests (
    id UUID PRIMARY KEY,
    booking_id UUID NOT NULL,
    current_slot_id UUID NOT NULL,
    proposed_slot_id UUID NOT NULL,
    requested_by_user_id UUID NOT NULL,
    requester_role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_reason TEXT NOT NULL,
    response_note TEXT,
    requested_at TIMESTAMP NOT NULL,
    responded_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_booking_reschedule_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id),
    CONSTRAINT fk_booking_reschedule_current_slot
        FOREIGN KEY (current_slot_id) REFERENCES mentor_availability_slots(id),
    CONSTRAINT fk_booking_reschedule_proposed_slot
        FOREIGN KEY (proposed_slot_id) REFERENCES mentor_availability_slots(id),
    CONSTRAINT fk_booking_reschedule_requested_by
        FOREIGN KEY (requested_by_user_id) REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS idx_booking_reschedule_booking_id
    ON booking_reschedule_requests (booking_id);

CREATE INDEX IF NOT EXISTS idx_booking_reschedule_status
    ON booking_reschedule_requests (status);

CREATE INDEX IF NOT EXISTS idx_booking_reschedule_requester
    ON booking_reschedule_requests (requester_role);
