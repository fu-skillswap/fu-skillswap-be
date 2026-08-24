-- rollout: EXPAND
-- Slice 7: Expand schema UTC for Google Calendar Connections, Sync Jobs, Event Links, and OAuth Accounts

-- 1. Add shadow TIMESTAMPTZ columns to google_calendar_connections
ALTER TABLE google_calendar_connections
    ADD COLUMN IF NOT EXISTS token_expires_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_sync_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 2. Add shadow TIMESTAMPTZ columns to google_calendar_sync_jobs
ALTER TABLE google_calendar_sync_jobs
    ADD COLUMN IF NOT EXISTS run_after_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS completed_at_utc TIMESTAMPTZ;

-- 3. Add shadow TIMESTAMPTZ columns to google_calendar_event_links
ALTER TABLE google_calendar_event_links
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 4. Add shadow TIMESTAMPTZ columns to oauth_accounts
ALTER TABLE oauth_accounts
    ADD COLUMN IF NOT EXISTS provider_token_expires_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS created_at_utc TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS updated_at_utc TIMESTAMPTZ;

-- 5. Backfill from legacy HCM timezone (Asia/Ho_Chi_Minh)
UPDATE google_calendar_connections
SET
    token_expires_at_utc = CASE WHEN token_expires_at IS NOT NULL THEN token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    last_sync_at_utc = CASE WHEN last_sync_at IS NOT NULL THEN last_sync_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE google_calendar_sync_jobs
SET
    run_after_utc = CASE WHEN run_after IS NOT NULL THEN run_after AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    completed_at_utc = CASE WHEN completed_at IS NOT NULL THEN completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE google_calendar_event_links
SET
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

UPDATE oauth_accounts
SET
    provider_token_expires_at_utc = CASE WHEN provider_token_expires_at IS NOT NULL THEN provider_token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    created_at_utc = CASE WHEN created_at IS NOT NULL THEN created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END,
    updated_at_utc = CASE WHEN updated_at IS NOT NULL THEN updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;

-- 6. PostgreSQL Triggers for dual-write / bidirectional sync

-- 6.1 google_calendar_connections sync trigger
CREATE OR REPLACE FUNCTION trg_sync_google_calendar_connections_utc() RETURNS trigger AS $$
BEGIN
    -- token_expires_at
    IF NEW.token_expires_at_utc IS DISTINCT FROM OLD.token_expires_at_utc AND NEW.token_expires_at IS NOT DISTINCT FROM OLD.token_expires_at THEN
        NEW.token_expires_at := CASE WHEN NEW.token_expires_at_utc IS NOT NULL THEN (NEW.token_expires_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.token_expires_at IS DISTINCT FROM OLD.token_expires_at AND NEW.token_expires_at_utc IS NOT DISTINCT FROM OLD.token_expires_at_utc THEN
        NEW.token_expires_at_utc := CASE WHEN NEW.token_expires_at IS NOT NULL THEN NEW.token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.token_expires_at_utc IS NOT NULL AND NEW.token_expires_at IS NULL THEN
            NEW.token_expires_at := (NEW.token_expires_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.token_expires_at IS NOT NULL AND NEW.token_expires_at_utc IS NULL THEN
            NEW.token_expires_at_utc := NEW.token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- last_sync_at
    IF NEW.last_sync_at_utc IS DISTINCT FROM OLD.last_sync_at_utc AND NEW.last_sync_at IS NOT DISTINCT FROM OLD.last_sync_at THEN
        NEW.last_sync_at := CASE WHEN NEW.last_sync_at_utc IS NOT NULL THEN (NEW.last_sync_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.last_sync_at IS DISTINCT FROM OLD.last_sync_at AND NEW.last_sync_at_utc IS NOT DISTINCT FROM OLD.last_sync_at_utc THEN
        NEW.last_sync_at_utc := CASE WHEN NEW.last_sync_at IS NOT NULL THEN NEW.last_sync_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.last_sync_at_utc IS NOT NULL AND NEW.last_sync_at IS NULL THEN
            NEW.last_sync_at := (NEW.last_sync_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.last_sync_at IS NOT NULL AND NEW.last_sync_at_utc IS NULL THEN
            NEW.last_sync_at_utc := NEW.last_sync_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- created_at
    IF NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.created_at IS DISTINCT FROM OLD.created_at AND NEW.created_at_utc IS NOT DISTINCT FROM OLD.created_at_utc THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.created_at_utc IS NOT NULL AND NEW.created_at IS NULL THEN
            NEW.created_at := (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.created_at IS NOT NULL AND NEW.created_at_utc IS NULL THEN
            NEW.created_at_utc := NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- updated_at
    IF NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.updated_at IS DISTINCT FROM OLD.updated_at AND NEW.updated_at_utc IS NOT DISTINCT FROM OLD.updated_at_utc THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.updated_at_utc IS NOT NULL AND NEW.updated_at IS NULL THEN
            NEW.updated_at := (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.updated_at IS NOT NULL AND NEW.updated_at_utc IS NULL THEN
            NEW.updated_at_utc := NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_google_calendar_connections_sync_utc ON google_calendar_connections;
CREATE TRIGGER trg_google_calendar_connections_sync_utc
    BEFORE INSERT OR UPDATE ON google_calendar_connections
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_google_calendar_connections_utc();

-- 6.2 google_calendar_sync_jobs sync trigger
CREATE OR REPLACE FUNCTION trg_sync_google_calendar_sync_jobs_utc() RETURNS trigger AS $$
BEGIN
    -- run_after
    IF NEW.run_after_utc IS DISTINCT FROM OLD.run_after_utc AND NEW.run_after IS NOT DISTINCT FROM OLD.run_after THEN
        NEW.run_after := CASE WHEN NEW.run_after_utc IS NOT NULL THEN (NEW.run_after_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.run_after IS DISTINCT FROM OLD.run_after AND NEW.run_after_utc IS NOT DISTINCT FROM OLD.run_after_utc THEN
        NEW.run_after_utc := CASE WHEN NEW.run_after IS NOT NULL THEN NEW.run_after AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.run_after_utc IS NOT NULL AND NEW.run_after IS NULL THEN
            NEW.run_after := (NEW.run_after_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.run_after IS NOT NULL AND NEW.run_after_utc IS NULL THEN
            NEW.run_after_utc := NEW.run_after AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- created_at
    IF NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.created_at IS DISTINCT FROM OLD.created_at AND NEW.created_at_utc IS NOT DISTINCT FROM OLD.created_at_utc THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.created_at_utc IS NOT NULL AND NEW.created_at IS NULL THEN
            NEW.created_at := (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.created_at IS NOT NULL AND NEW.created_at_utc IS NULL THEN
            NEW.created_at_utc := NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- updated_at
    IF NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.updated_at IS DISTINCT FROM OLD.updated_at AND NEW.updated_at_utc IS NOT DISTINCT FROM OLD.updated_at_utc THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.updated_at_utc IS NOT NULL AND NEW.updated_at IS NULL THEN
            NEW.updated_at := (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.updated_at IS NOT NULL AND NEW.updated_at_utc IS NULL THEN
            NEW.updated_at_utc := NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- completed_at
    IF NEW.completed_at_utc IS DISTINCT FROM OLD.completed_at_utc AND NEW.completed_at IS NOT DISTINCT FROM OLD.completed_at THEN
        NEW.completed_at := CASE WHEN NEW.completed_at_utc IS NOT NULL THEN (NEW.completed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.completed_at IS DISTINCT FROM OLD.completed_at AND NEW.completed_at_utc IS NOT DISTINCT FROM OLD.completed_at_utc THEN
        NEW.completed_at_utc := CASE WHEN NEW.completed_at IS NOT NULL THEN NEW.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.completed_at_utc IS NOT NULL AND NEW.completed_at IS NULL THEN
            NEW.completed_at := (NEW.completed_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.completed_at IS NOT NULL AND NEW.completed_at_utc IS NULL THEN
            NEW.completed_at_utc := NEW.completed_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_google_calendar_sync_jobs_sync_utc ON google_calendar_sync_jobs;
CREATE TRIGGER trg_google_calendar_sync_jobs_sync_utc
    BEFORE INSERT OR UPDATE ON google_calendar_sync_jobs
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_google_calendar_sync_jobs_utc();

-- 6.3 google_calendar_event_links sync trigger
CREATE OR REPLACE FUNCTION trg_sync_google_calendar_event_links_utc() RETURNS trigger AS $$
BEGIN
    -- created_at
    IF NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.created_at IS DISTINCT FROM OLD.created_at AND NEW.created_at_utc IS NOT DISTINCT FROM OLD.created_at_utc THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.created_at_utc IS NOT NULL AND NEW.created_at IS NULL THEN
            NEW.created_at := (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.created_at IS NOT NULL AND NEW.created_at_utc IS NULL THEN
            NEW.created_at_utc := NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- updated_at
    IF NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.updated_at IS DISTINCT FROM OLD.updated_at AND NEW.updated_at_utc IS NOT DISTINCT FROM OLD.updated_at_utc THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.updated_at_utc IS NOT NULL AND NEW.updated_at IS NULL THEN
            NEW.updated_at := (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.updated_at IS NOT NULL AND NEW.updated_at_utc IS NULL THEN
            NEW.updated_at_utc := NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_google_calendar_event_links_sync_utc ON google_calendar_event_links;
CREATE TRIGGER trg_google_calendar_event_links_sync_utc
    BEFORE INSERT OR UPDATE ON google_calendar_event_links
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_google_calendar_event_links_utc();

-- 6.4 oauth_accounts sync trigger
CREATE OR REPLACE FUNCTION trg_sync_oauth_accounts_utc() RETURNS trigger AS $$
BEGIN
    -- provider_token_expires_at
    IF NEW.provider_token_expires_at_utc IS DISTINCT FROM OLD.provider_token_expires_at_utc AND NEW.provider_token_expires_at IS NOT DISTINCT FROM OLD.provider_token_expires_at THEN
        NEW.provider_token_expires_at := CASE WHEN NEW.provider_token_expires_at_utc IS NOT NULL THEN (NEW.provider_token_expires_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.provider_token_expires_at IS DISTINCT FROM OLD.provider_token_expires_at AND NEW.provider_token_expires_at_utc IS NOT DISTINCT FROM OLD.provider_token_expires_at_utc THEN
        NEW.provider_token_expires_at_utc := CASE WHEN NEW.provider_token_expires_at IS NOT NULL THEN NEW.provider_token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.provider_token_expires_at_utc IS NOT NULL AND NEW.provider_token_expires_at IS NULL THEN
            NEW.provider_token_expires_at := (NEW.provider_token_expires_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.provider_token_expires_at IS NOT NULL AND NEW.provider_token_expires_at_utc IS NULL THEN
            NEW.provider_token_expires_at_utc := NEW.provider_token_expires_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- created_at
    IF NEW.created_at_utc IS DISTINCT FROM OLD.created_at_utc AND NEW.created_at IS NOT DISTINCT FROM OLD.created_at THEN
        NEW.created_at := CASE WHEN NEW.created_at_utc IS NOT NULL THEN (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.created_at IS DISTINCT FROM OLD.created_at AND NEW.created_at_utc IS NOT DISTINCT FROM OLD.created_at_utc THEN
        NEW.created_at_utc := CASE WHEN NEW.created_at IS NOT NULL THEN NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.created_at_utc IS NOT NULL AND NEW.created_at IS NULL THEN
            NEW.created_at := (NEW.created_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.created_at IS NOT NULL AND NEW.created_at_utc IS NULL THEN
            NEW.created_at_utc := NEW.created_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    -- updated_at
    IF NEW.updated_at_utc IS DISTINCT FROM OLD.updated_at_utc AND NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := CASE WHEN NEW.updated_at_utc IS NOT NULL THEN (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE ELSE NULL END;
    ELSIF NEW.updated_at IS DISTINCT FROM OLD.updated_at AND NEW.updated_at_utc IS NOT DISTINCT FROM OLD.updated_at_utc THEN
        NEW.updated_at_utc := CASE WHEN NEW.updated_at IS NOT NULL THEN NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh' ELSE NULL END;
    ELSIF TG_OP = 'INSERT' THEN
        IF NEW.updated_at_utc IS NOT NULL AND NEW.updated_at IS NULL THEN
            NEW.updated_at := (NEW.updated_at_utc AT TIME ZONE 'Asia/Ho_Chi_Minh')::timestamp WITHOUT TIME ZONE;
        ELSIF NEW.updated_at IS NOT NULL AND NEW.updated_at_utc IS NULL THEN
            NEW.updated_at_utc := NEW.updated_at AT TIME ZONE 'Asia/Ho_Chi_Minh';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_oauth_accounts_sync_utc ON oauth_accounts;
CREATE TRIGGER trg_oauth_accounts_sync_utc
    BEFORE INSERT OR UPDATE ON oauth_accounts
    FOR EACH ROW
    EXECUTE FUNCTION trg_sync_oauth_accounts_utc();

-- 7. Performance Indexes
CREATE INDEX IF NOT EXISTS idx_google_calendar_sync_jobs_poll_utc
    ON google_calendar_sync_jobs (status, run_after_utc);
