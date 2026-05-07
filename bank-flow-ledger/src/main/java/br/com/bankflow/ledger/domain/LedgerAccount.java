package br.com.bankflow.ledger.domain;

import java.util.UUID;

public record LedgerAccount(
		long accountId,
		String accountCode,
		String accountName,
		String accountType,
	String normalBalance,
	String currency,
	String ownerType,
	UUID digitalAccountId,
	boolean active,
	long createdAt
) {
	private static final String ACCOUNT_TYPE = "LIABILITY";
	private static final String NORMAL_BALANCE = "CREDIT";
	private static final String CURRENCY = "BRL";
	private static final String OWNER_TYPE = "BANK_ACCOUNT";

	public static LedgerAccount from(long accountId, long createdAt, AccountCreatedEvent event) {
		event.validate();
		if (accountId <= 0) {
			throw new IllegalArgumentException("account_id must be positive");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}
		if (!CURRENCY.equals(event.currency())) {
			throw new IllegalArgumentException("ledger account creation only supports BRL accounts");
		}

		return new LedgerAccount(
				accountId,
				"CUSTOMER_ACCOUNT_%s".formatted(event.digitalAccountId()),
				"Saldo dispon\u00edvel BRL - Conta %s".formatted(event.account()),
				ACCOUNT_TYPE,
				NORMAL_BALANCE,
				CURRENCY,
				OWNER_TYPE,
				event.digitalAccountId(),
				true,
				createdAt
		);
	}
}
