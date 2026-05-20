create schema if not exists transfers;

create table transfers.transfers (
	id uuid primary key,
	source_account_id uuid not null,
	destination_account_id uuid not null,
	idempotency_key varchar(64) not null,
	amount_minor bigint not null,
	description varchar(255),
	currency char(3) not null,
	type varchar(32) not null,
	status varchar(64) not null,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	constraint uk_transfers_idempotency_key unique (idempotency_key),
	constraint ck_transfers_amount_minor_positive check (amount_minor > 0)
);

create index idx_transfers_source_account_id on transfers.transfers (source_account_id);
create index idx_transfers_destination_account_id on transfers.transfers (destination_account_id);
create index idx_transfers_status on transfers.transfers (status);
