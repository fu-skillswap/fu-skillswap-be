create table forum_posts (
    id uuid not null,
    author_user_id uuid not null,
    help_topic_id uuid not null,
    title varchar(200) not null,
    content text not null,
    status varchar(30) not null,
    comment_count integer not null default 0,
    reaction_count integer not null default 0,
    report_count integer not null default 0,
    last_activity_at timestamp(6) not null,
    hidden_at timestamp(6),
    hidden_by_user_id uuid,
    hidden_reason varchar(500),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    deleted_at timestamp(6),
    primary key (id),
    constraint fk_forum_posts_author foreign key (author_user_id) references users(id),
    constraint fk_forum_posts_help_topic foreign key (help_topic_id) references tags(id)
);

create table forum_comments (
    id uuid not null,
    post_id uuid not null,
    author_user_id uuid not null,
    content text not null,
    status varchar(30) not null,
    report_count integer not null default 0,
    hidden_at timestamp(6),
    hidden_by_user_id uuid,
    hidden_reason varchar(500),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    deleted_at timestamp(6),
    primary key (id),
    constraint fk_forum_comments_post foreign key (post_id) references forum_posts(id),
    constraint fk_forum_comments_author foreign key (author_user_id) references users(id)
);

create table forum_post_reactions (
    id uuid not null,
    post_id uuid not null,
    user_id uuid not null,
    reaction_type varchar(20) not null,
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint fk_forum_post_reactions_post foreign key (post_id) references forum_posts(id),
    constraint fk_forum_post_reactions_user foreign key (user_id) references users(id),
    constraint uq_forum_post_reactions_post_user unique (post_id, user_id)
);

create table forum_action_logs (
    id uuid not null,
    user_id uuid not null,
    action_type varchar(30) not null,
    created_at timestamp(6) not null,
    primary key (id),
    constraint fk_forum_action_logs_user foreign key (user_id) references users(id)
);

create table forum_reports (
    id uuid not null,
    reporter_user_id uuid not null,
    target_type varchar(20) not null,
    target_id uuid not null,
    reason_type varchar(30) not null,
    description text,
    status varchar(40) not null,
    reviewed_by_user_id uuid,
    review_note varchar(500),
    resolved_at timestamp(6),
    created_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint fk_forum_reports_reporter foreign key (reporter_user_id) references users(id),
    constraint uq_forum_reports_reporter_target unique (reporter_user_id, target_type, target_id)
);

create index idx_forum_posts_status_created on forum_posts (status, created_at);
create index idx_forum_posts_help_topic_created on forum_posts (help_topic_id, created_at);
create index idx_forum_posts_author_created on forum_posts (author_user_id, created_at);
create index idx_forum_comments_post_status_created on forum_comments (post_id, status, created_at);
create index idx_forum_comments_author_created on forum_comments (author_user_id, created_at);
create index idx_forum_action_logs_user_action_created on forum_action_logs (user_id, action_type, created_at);
create index idx_forum_reports_status_created on forum_reports (status, created_at);
create index idx_forum_reports_target on forum_reports (target_type, target_id);
