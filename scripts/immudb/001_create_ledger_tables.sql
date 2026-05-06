CREATE TABLE IF NOT EXISTS ledger_accounts (
    account_id     INTEGER,
    account_code   VARCHAR[128] NOT NULL,
    account_name   VARCHAR[256] NOT NULL,
    account_type   VARCHAR[32] NOT NULL,
    normal_balance VARCHAR[16] NOT NULL,
    currency       VARCHAR[3] NOT NULL,
    owner_type     VARCHAR[64] NOT NULL,
    owner_id       UUID NOT NULL,
    active         BOOLEAN NOT NULL,
    created_at     INTEGER NOT NULL,
    owner_id_old   INTEGER,
    PRIMARY KEY (account_id)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    entry_id             INTEGER,
    external_id          VARCHAR[128] NOT NULL,
    entry_type           VARCHAR[64] NOT NULL,
    status               VARCHAR[32] NOT NULL,
    description          VARCHAR[512] NOT NULL,
    occurred_at          INTEGER NOT NULL,
    created_at           INTEGER NOT NULL,
    reversal_of_entry_id INTEGER,
    metadata             JSON[4096] NOT NULL,
    PRIMARY KEY (entry_id)
);

CREATE TABLE IF NOT EXISTS ledger_entry_lines (
    line_id             INTEGER,
    entry_id            INTEGER NOT NULL,
    account_id          INTEGER NOT NULL,
    direction           VARCHAR[6] NOT NULL,
    amount_minor        INTEGER NOT NULL,
    signed_amount_minor INTEGER NOT NULL,
    currency            VARCHAR[3] NOT NULL,
    line_memo           VARCHAR[512] NOT NULL,
    created_at          INTEGER NOT NULL,
    PRIMARY KEY (line_id)
);

CREATE UNIQUE INDEX ON ledger_accounts(account_code);
CREATE INDEX ON ledger_accounts(owner_id);
CREATE UNIQUE INDEX ON ledger_entries(external_id);
CREATE INDEX ON ledger_entry_lines(entry_id);
CREATE INDEX ON ledger_entry_lines(account_id);
