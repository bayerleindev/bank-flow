create table balance.hold_settlements (
	transfer_id uuid not null,
	type varchar(16) not null,
	status varchar(16) not null,
	processed_at timestamptz not null,
	primary key (transfer_id, type)
);
