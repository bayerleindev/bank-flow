alter table transfers.transfers
	alter column debit_bank drop not null,
	alter column debit_account drop not null,
	alter column debit_branch drop not null;

create index idx_transfers_debit_account_id on transfers.transfers (debit_account_id);
