-- Rollback Note:
-- Destructive: No (Adds column and migrates data).
-- Rollback Strategy: App rollback is possible. If needed, DB rollback can be done manually via:
-- ALTER TABLE email_outbox DROP COLUMN payload_data;
-- Affect on old data: Old data is migrated gracefully with JSON built from existing columns.

alter table email_outbox
    add column if not exists payload_data jsonb;

update email_outbox
set payload_data = jsonb_build_object(
        'toEmail', to_email,
        'subject', subject,
        'htmlBody', body,
        'plainTextFallback', body,
        'templateCode', template_code
    )
where payload_data is null;

alter table email_outbox
    alter column payload_data set not null;
