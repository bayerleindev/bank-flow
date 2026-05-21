create schema if not exists auth;

create table auth.credentials (
	id uuid primary key,
	onboarding_application_id uuid not null,
	document_number varchar(32) not null,
	password_hash varchar(255) not null,
	status varchar(32) not null,
	created_at timestamptz not null,
	updated_at timestamptz not null
);

create unique index idx_credentials_document_number on auth.credentials (document_number);
create unique index idx_credentials_onboarding_application_id
	on auth.credentials (onboarding_application_id);
