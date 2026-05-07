CREATE TABLE outbox_events (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    topic VARCHAR(128) NOT NULL,
    event_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error VARCHAR(512),
    created_at BIGINT NOT NULL,
    published_at BIGINT,
    CONSTRAINT chk_outbox_events_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED'))
);

CREATE INDEX idx_outbox_events_status_created
ON outbox_events (status, created_at);

CREATE UNIQUE INDEX ux_outbox_events_aggregate_event
ON outbox_events (aggregate_type, aggregate_id, event_type);
