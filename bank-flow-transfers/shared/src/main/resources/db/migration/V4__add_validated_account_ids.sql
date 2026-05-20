alter table transfers.transfers
	add column debit_account_id uuid,
	add column credit_account_id uuid;
