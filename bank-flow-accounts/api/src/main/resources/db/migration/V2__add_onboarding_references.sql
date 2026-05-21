alter table accounts.accounts
	add column onboarding_application_id uuid,
	add column credentials_id uuid;

create unique index idx_accounts_onboarding_application_id
	on accounts.accounts (onboarding_application_id)
	where onboarding_application_id is not null;

create unique index idx_accounts_credentials_id
	on accounts.accounts (credentials_id)
	where credentials_id is not null;
