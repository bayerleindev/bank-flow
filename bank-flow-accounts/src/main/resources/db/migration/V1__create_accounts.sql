CREATE TABLE accounts (
    account_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    owner_id UUID NOT NULL,
    full_name VARCHAR(256) NOT NULL,
    document_number VARCHAR(32) NOT NULL,
    email VARCHAR(256) NOT NULL,
    mother_name VARCHAR(256) NOT NULL,
    social_name VARCHAR(256),
    phone_number VARCHAR(32) NOT NULL,
    birth_date DATE NOT NULL,
    address VARCHAR(512) NOT NULL,
    is_politically_exposed BOOLEAN NOT NULL,
    baas_account_id VARCHAR(128),
    branch VARCHAR(32),
    account VARCHAR(128),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    failure_reason VARCHAR(256),
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    CONSTRAINT ux_accounts_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT ux_accounts_document_number UNIQUE (document_number),
    CONSTRAINT ux_accounts_owner_id UNIQUE (owner_id),
    CONSTRAINT chk_accounts_currency_length CHECK (char_length(currency) = 3),
    CONSTRAINT chk_accounts_status CHECK (status IN ('RECEIVED', 'BAAS_PENDING', 'ACTIVE', 'REJECTED', 'FAILED'))
);

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

CREATE INDEX idx_accounts_status_updated
ON accounts (status, updated_at);

CREATE INDEX idx_outbox_events_status_created
ON outbox_events (status, created_at);

CREATE UNIQUE INDEX ux_outbox_events_aggregate_event
ON outbox_events (aggregate_type, aggregate_id, event_type);
