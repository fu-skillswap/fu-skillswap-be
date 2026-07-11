create table if not exists admin_case_assignments (
    id uuid not null,
    case_type varchar(80) not null,
    case_id uuid not null,
    assigned_admin_user_id uuid not null,
    assigned_at timestamp(6) not null,
    updated_at timestamp(6) not null,
    primary key (id),
    constraint uq_admin_case_assignments_case unique (case_type, case_id),
    constraint fk_admin_case_assignments_admin
        foreign key (assigned_admin_user_id) references users (id)
);

create index if not exists idx_admin_case_assignments_admin_id
    on admin_case_assignments (assigned_admin_user_id);

create index if not exists idx_admin_case_assignments_case
    on admin_case_assignments (case_type, case_id);

create index if not exists idx_audit_logs_entity_type_entity_id_created_at
    on audit_logs (entity_type, entity_id, created_at desc);
