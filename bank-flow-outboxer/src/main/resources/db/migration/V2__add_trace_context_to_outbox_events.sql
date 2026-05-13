ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS traceparent VARCHAR(128);

ALTER TABLE outbox_events
    ADD COLUMN IF NOT EXISTS tracestate TEXT;
