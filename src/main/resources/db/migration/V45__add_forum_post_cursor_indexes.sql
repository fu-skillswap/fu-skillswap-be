create index if not exists idx_forum_posts_status_last_activity_id
    on forum_posts (status, last_activity_at desc, id desc);

create index if not exists idx_forum_posts_help_topic_status_last_activity_id
    on forum_posts (help_topic_id, status, last_activity_at desc, id desc);

create index if not exists idx_forum_posts_author_status_last_activity_id
    on forum_posts (author_user_id, status, last_activity_at desc, id desc);
