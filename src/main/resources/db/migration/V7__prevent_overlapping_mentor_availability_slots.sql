create extension if not exists btree_gist;

alter table mentor_availability_slots
    add constraint ex_mentor_availability_slots_no_overlap
    exclude using gist (
        mentor_user_id with =,
        tsrange(start_time, end_time, '[)') with &&
    )
    where (is_active);
