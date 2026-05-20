create schema if not exists accounts;

create type accounts.account_status as enum (
	'CREATION_REQUESTED',
	'PENDING_BAAS',
	'ACTIVE',
	'REJECTED'
);

create table accounts.accounts (
	id uuid primary key,
	full_name varchar(255) not null,
	document_number varchar(32) not null,
	email varchar(255) not null,
	mother_name varchar(255) not null,
	social_name varchar(255),
	phone_number varchar(32) not null,
	birth_date date not null,
	address varchar(500) not null,
	is_politically_exposed boolean not null,
	status accounts.account_status not null,
	branch_number varchar(16),
	account_number varchar(32),
	account_digit varchar(8),
	rejection_reason varchar(500),
	created_at timestamptz not null,
	updated_at timestamptz not null
);

create index idx_accounts_document_number on accounts.accounts (document_number);
create index idx_accounts_status on accounts.accounts (status);
