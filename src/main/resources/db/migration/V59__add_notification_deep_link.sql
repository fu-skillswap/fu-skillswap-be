alter table notifications
    add column if not exists deep_link varchar(500);
