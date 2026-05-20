alter table transfers.transfers
	add column debit_bank varchar(32),
	add column debit_account varchar(64),
	add column debit_branch varchar(32),
	add column credit_bank varchar(32),
	add column credit_account varchar(64),
	add column credit_branch varchar(32);

update transfers.transfers
set debit_bank = 'UNKNOWN',
	debit_account = source_account_id::text,
	debit_branch = 'UNKNOWN',
	credit_bank = 'UNKNOWN',
	credit_account = destination_account_id::text,
	credit_branch = 'UNKNOWN'
where debit_bank is null;

alter table transfers.transfers
	alter column debit_bank set not null,
	alter column debit_account set not null,
	alter column debit_branch set not null,
	alter column credit_bank set not null,
	alter column credit_account set not null,
	alter column credit_branch set not null;

drop index if exists transfers.idx_transfers_source_account_id;
drop index if exists transfers.idx_transfers_destination_account_id;

alter table transfers.transfers
	drop column source_account_id,
	drop column destination_account_id;

create index idx_transfers_debit_party on transfers.transfers (debit_bank, debit_branch, debit_account);
create index idx_transfers_credit_party on transfers.transfers (credit_bank, credit_branch, credit_account);
