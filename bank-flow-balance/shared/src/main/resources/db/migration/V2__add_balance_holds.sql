alter table balance.account_balances
	add column held_amount_minor bigint not null default 0;

create table balance.balance_holds (
	transfer_id uuid not null,
	account_id uuid not null,
	amount_minor bigint not null,
	currency varchar(3) not null,
	status varchar(16) not null,
	reason varchar(64),
	created_at timestamptz not null,
	primary key (transfer_id)
);
