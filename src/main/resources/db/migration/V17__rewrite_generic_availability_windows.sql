ALTER TABLE mentor_availability_slots
    ADD COLUMN IF NOT EXISTS rule_id uuid;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.table_constraints
        WHERE constraint_name = 'fk_availability_rule'
          AND table_name = 'mentor_availability_slots'
    ) THEN
        ALTER TABLE mentor_availability_slots
            ADD CONSTRAINT fk_availability_rule
                FOREIGN KEY (rule_id) REFERENCES mentor_availability_rules(id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_availability_slots_rule_id
    ON mentor_availability_slots (rule_id);

UPDATE bookings booking
SET status = 'REJECTED',
    rejected_at = NOW(),
    reject_reason = 'Khung giờ mentoring cũ không còn hiệu lực sau khi hệ thống chuyển sang lịch availability mới.'
FROM mentor_availability_slots slot
WHERE booking.slot_id = slot.id
  AND booking.status = 'PENDING'
  AND slot.rule_id IS NULL
  AND slot.start_time >= NOW();

UPDATE mentor_availability_slots
SET is_active = false,
    is_booked = false
WHERE rule_id IS NULL
  AND start_time >= NOW()
  AND is_booked = false
  AND is_active = true;
