-- rollout: EXPAND
-- V134 added these columns as required fields for the new application version.
-- Keep them nullable during rolling deployment so older instances can still write
-- forum_action_logs until the contract migration is released separately.

alter table forum_action_logs alter column target_type drop not null;
alter table forum_action_logs alter column metadata drop not null;
