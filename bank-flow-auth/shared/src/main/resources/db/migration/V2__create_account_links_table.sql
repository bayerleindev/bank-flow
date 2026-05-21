create table auth.account_links (
	document_number varchar(32) primary key,
	account_id uuid not null unique,
	branch_number varchar(16) not null,
	account_number varchar(32) not null,
	account_digit varchar(8) not null,
	created_at timestamptz not null,
	updated_at timestamptz not null
);
