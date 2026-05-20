create schema if not exists balance;

create table balance.account_balances (
	account_id uuid not null,
	currency varchar(3) not null,
	available_amount_minor bigint not null,
	updated_at timestamptz not null,
	primary key (account_id, currency)
);

create table balance.processed_journal_entries (
	movement_id uuid not null,
	account_id uuid not null,
	side varchar(8) not null,
	processed_at timestamptz not null,
	primary key (movement_id, account_id, side)
);
