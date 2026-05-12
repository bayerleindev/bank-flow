ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_by VARCHAR(128);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS locked_until BIGINT;

ALTER TABLE outbox_events
    DROP CONSTRAINT IF EXISTS chk_outbox_events_status;

ALTER TABLE outbox_events
    ADD CONSTRAINT chk_outbox_events_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'PUBLISHED', 'FAILED'));

CREATE INDEX IF NOT EXISTS idx_outbox_events_claimable
ON outbox_events (status, locked_until, created_at);
