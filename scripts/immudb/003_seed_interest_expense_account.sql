INSERT INTO ledger_accounts (
    account_id,
    account_code,
    account_name,
    account_type,
    normal_balance,
    currency,
    owner_type,
    owner_id,
    active,
    created_at,
    owner_id_old
) VALUES (
    900000002,
    'INTEREST_EXPENSE_CDI_BRL',
    'Despesa de rendimento CDI de contas digitais - BRL',
    'EXPENSE',
    'DEBIT',
    'BRL',
    'BANK_INTERNAL_ACCOUNT',
    '00000000-0000-0000-0000-000000000200'::UUID,
    true,
    1778100000000,
    1
);
