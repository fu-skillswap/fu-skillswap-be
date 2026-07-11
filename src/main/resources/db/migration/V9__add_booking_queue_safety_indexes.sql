-- Protect queue booking invariants at the database level.
-- Note: this migration assumes there is no legacy duplicate data violating these rules.

create unique index if not exists uq_bookings_slot_accepted
    on bookings (slot_id)
    where slot_id is not null
      and status = 'ACCEPTED';

create unique index if not exists uq_bookings_mentee_slot_active
    on bookings (mentee_user_id, slot_id)
    where slot_id is not null
      and status in ('PENDING', 'ACCEPTED');
