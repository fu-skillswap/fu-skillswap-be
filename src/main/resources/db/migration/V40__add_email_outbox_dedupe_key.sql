alter table email_outbox
    add column if not exists dedupe_key varchar(180);

create unique index if not exists ux_email_outbox_dedupe_key
    on email_outbox (dedupe_key)
    where dedupe_key is not null;
