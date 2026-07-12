create table if not exists blog_category_follows (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    category_id uuid not null references blog_categories(id) on delete cascade,
    created_at timestamp not null default now(),
    constraint uk_blog_category_follows_user_category unique (user_id, category_id)
);

create table if not exists blog_tag_follows (
    id uuid primary key,
    user_id uuid not null references users(id) on delete cascade,
    tag_id uuid not null references blog_tags(id) on delete cascade,
    created_at timestamp not null default now(),
    constraint uk_blog_tag_follows_user_tag unique (user_id, tag_id)
);

create index if not exists idx_blog_category_follows_user_created
    on blog_category_follows (user_id, created_at desc);

create index if not exists idx_blog_category_follows_category_user
    on blog_category_follows (category_id, user_id);

create index if not exists idx_blog_tag_follows_user_created
    on blog_tag_follows (user_id, created_at desc);

create index if not exists idx_blog_tag_follows_tag_user
    on blog_tag_follows (tag_id, user_id);
