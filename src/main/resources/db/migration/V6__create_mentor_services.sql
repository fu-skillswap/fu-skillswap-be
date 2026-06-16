create table if not exists mentor_services (
    id uuid primary key,
    mentor_user_id uuid not null,
    title varchar(200) not null,
    description text,
    duration_minutes integer not null default 60,
    is_free boolean not null default true,
    price_amount numeric(12,2) not null default 0,
    currency varchar(10) not null default 'VND',
    is_active boolean not null default true,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint fk_mentor_services_mentor foreign key (mentor_user_id) references users(id)
);

create table if not exists mentor_service_help_topics (
    service_id uuid not null,
    tag_id uuid not null,
    primary key (service_id, tag_id),
    constraint fk_service_help_topics_service foreign key (service_id) references mentor_services(id) on delete cascade,
    constraint fk_service_help_topics_tag foreign key (tag_id) references tags(id)
);

create index if not exists idx_mentor_services_mentor_id on mentor_services (mentor_user_id);
create index if not exists idx_mentor_services_active on mentor_services (is_active);
create index if not exists idx_mentor_service_help_topics_tag on mentor_service_help_topics (tag_id);
