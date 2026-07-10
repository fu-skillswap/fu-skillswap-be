alter table if exists user_sessions
    add column if not exists session_state varchar(30),
    add column if not exists grace_expires_at timestamp(6),
    add column if not exists grace_replacement_session_id uuid,
    add column if not exists grace_replay_ciphertext TEXT;

update user_sessions
set session_state = case
    when is_revoked then 'REVOKED'
    else 'ACTIVE'
end
where session_state is null;

alter table if exists user_sessions
    alter column session_state set default 'ACTIVE',
    alter column session_state set not null;

create index if not exists idx_user_sessions_grace_replacement_session_id
    on user_sessions (grace_replacement_session_id);
