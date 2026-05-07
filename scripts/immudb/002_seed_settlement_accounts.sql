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
    900000001,
    'SETTLEMENT_EXTERNAL_INBOUND_BRL',
    'Liquidacao de transferencias recebidas de outras instituicoes - BRL',
    'ASSET',
    'DEBIT',
    'BRL',
    'SETTLEMENT_ACCOUNT',
    '00000000-0000-0000-0000-000000000100'::UUID,
    true,
    1778100000000,
    1
);
