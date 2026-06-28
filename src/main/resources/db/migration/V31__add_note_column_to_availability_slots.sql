-- Migration V31: Add note column to mentor_availability_slots to support slot-centric notes directly
ALTER TABLE mentor_availability_slots ADD COLUMN IF NOT EXISTS note VARCHAR(200);
