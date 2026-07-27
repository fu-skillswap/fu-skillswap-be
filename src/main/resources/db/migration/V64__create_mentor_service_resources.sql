create table mentor_service_resources (
 id uuid primary key, service_id uuid not null references mentor_services(id), title varchar(255) not null,
 description text, resource_type varchar(20) not null, visibility varchar(30) not null,
 storage_key varchar(512) not null unique, content_type varchar(120) not null, size_bytes bigint not null,
 deleted_at timestamp, version integer not null default 0, created_at timestamp not null, updated_at timestamp not null
);
create index idx_mentor_service_resources_service_active on mentor_service_resources(service_id, created_at) where deleted_at is null;
create table mentor_service_resource_upload_intents (
 id uuid primary key, service_id uuid not null references mentor_services(id), storage_key varchar(512) not null unique,
 expected_type varchar(20) not null, status varchar(30) not null, expires_at timestamp not null,
 resource_id uuid unique references mentor_service_resources(id), version integer not null default 0, created_at timestamp not null
);
create index idx_mentor_service_resource_intent_expiry on mentor_service_resource_upload_intents(status, expires_at);
create table mentor_service_resource_access_logs (
 id uuid primary key, resource_id uuid not null references mentor_service_resources(id), user_id uuid not null,
 action varchar(40) not null, success boolean not null, failure_reason_code varchar(80), created_at timestamp not null
);
