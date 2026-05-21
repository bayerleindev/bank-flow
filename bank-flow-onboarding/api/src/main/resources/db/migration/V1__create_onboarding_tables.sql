create schema if not exists onboarding;

create type onboarding.application_status as enum (
	'DRAFT',
	'DOCUMENTS_UPLOADED',
	'CREDENTIALS_CREATED',
	'SUBMITTED',
	'UNDER_REVIEW',
	'APPROVED',
	'REJECTED',
	'EXPIRED',
	'CANCELLED'
);

create type onboarding.document_type as enum (
	'DOCUMENT_FRONT',
	'DOCUMENT_BACK',
	'SELFIE',
	'PROOF_OF_ADDRESS'
);

create type onboarding.document_status as enum (
	'PENDING_UPLOAD',
	'UPLOADED',
	'VALIDATED',
	'REJECTED'
);

create table onboarding.onboarding_applications (
	id uuid primary key,
	status onboarding.application_status not null,
	full_name varchar(255) not null,
	document_number varchar(32) not null,
	email varchar(255) not null,
	mother_name varchar(255) not null,
	social_name varchar(255),
	phone_number varchar(32) not null,
	birth_date date not null,
	address varchar(500) not null,
	is_politically_exposed boolean not null,
	credentials_id uuid,
	rejection_reason_code varchar(100),
	application_token_hash varchar(64) not null,
	application_token_expires_at timestamptz not null,
	created_at timestamptz not null,
	updated_at timestamptz not null,
	submitted_at timestamptz,
	approved_at timestamptz,
	rejected_at timestamptz
);

create unique index idx_onboarding_applications_document_number
	on onboarding.onboarding_applications (document_number);
create index idx_onboarding_applications_status
	on onboarding.onboarding_applications (status);
create index idx_onboarding_applications_token_hash
	on onboarding.onboarding_applications (application_token_hash);

create table onboarding.onboarding_documents (
	id uuid primary key,
	application_id uuid not null references onboarding.onboarding_applications (id),
	type onboarding.document_type not null,
	status onboarding.document_status not null,
	storage_key varchar(500) not null,
	content_type varchar(100) not null,
	content_length bigint,
	content_hash varchar(128),
	rejection_reason_code varchar(100),
	created_at timestamptz not null,
	updated_at timestamptz not null,
	uploaded_at timestamptz
);

create index idx_onboarding_documents_application_id
	on onboarding.onboarding_documents (application_id);
create unique index idx_onboarding_documents_storage_key
	on onboarding.onboarding_documents (storage_key);
