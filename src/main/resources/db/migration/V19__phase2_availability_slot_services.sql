CREATE TABLE IF NOT EXISTS availability_slot_services (
    slot_id UUID NOT NULL,
    service_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT pk_availability_slot_services PRIMARY KEY (slot_id, service_id),
    CONSTRAINT fk_availability_slot_services_slot FOREIGN KEY (slot_id) REFERENCES mentor_availability_slots(id) ON DELETE CASCADE,
    CONSTRAINT fk_availability_slot_services_service FOREIGN KEY (service_id) REFERENCES mentor_services(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_availability_slot_services_service_id
    ON availability_slot_services (service_id);

CREATE INDEX IF NOT EXISTS idx_bookings_slot_status_selected_segment
    ON bookings (slot_id, status, selected_start_time, selected_end_time);
