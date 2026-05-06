CREATE TABLE account_balances (
    account_id BIGINT PRIMARY KEY,
    currency VARCHAR(3) NOT NULL,
    posted_minor BIGINT NOT NULL DEFAULT 0,
    updated_at BIGINT NOT NULL,
    CONSTRAINT chk_account_balances_currency_length CHECK (char_length(currency) = 3)
);

CREATE TABLE account_balance_entries (
    line_id BIGINT PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    external_id VARCHAR(128) NOT NULL,
    entry_type VARCHAR(64) NOT NULL,
    direction VARCHAR(6) NOT NULL,
    amount_minor BIGINT NOT NULL,
    signed_amount_minor BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    description VARCHAR(512) NOT NULL,
    occurred_at BIGINT NOT NULL,
    created_at BIGINT NOT NULL,
    CONSTRAINT chk_account_balance_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_account_balance_entries_amount_positive CHECK (amount_minor > 0),
    CONSTRAINT chk_account_balance_entries_currency_length CHECK (char_length(currency) = 3)
);

CREATE TABLE processed_ledger_entries (
    entry_id BIGINT PRIMARY KEY,
    external_id VARCHAR(128) NOT NULL,
    processed_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX ux_processed_ledger_entries_external_id
ON processed_ledger_entries (external_id);

CREATE INDEX idx_account_balance_entries_account_occurred
ON account_balance_entries (account_id, occurred_at DESC);

CREATE INDEX idx_account_balance_entries_entry_id
ON account_balance_entries (entry_id);
