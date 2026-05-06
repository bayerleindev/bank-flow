package br.com.bankflow.ledger.domain;

public record LedgerEntryLine(
		long lineId,
		long entryId,
		long accountId,
		String direction,
		long amountMinor,
		long signedAmountMinor,
		String currency,
		String lineMemo,
		long createdAt
) {
	public static LedgerEntryLine debit(
			long lineId,
			long entryId,
			long accountId,
			long amountMinor,
			String currency,
			String lineMemo,
			long createdAt
	) {
		return create(lineId, entryId, accountId, "DEBIT", amountMinor, -amountMinor, currency, lineMemo, createdAt);
	}

	public static LedgerEntryLine credit(
			long lineId,
			long entryId,
			long accountId,
			long amountMinor,
			String currency,
			String lineMemo,
			long createdAt
	) {
		return create(lineId, entryId, accountId, "CREDIT", amountMinor, amountMinor, currency, lineMemo, createdAt);
	}

	public static LedgerEntryLine reversalOf(
			long lineId,
			long entryId,
			LedgerEntryLine originalLine,
			String lineMemo,
			long createdAt
	) {
		if ("DEBIT".equals(originalLine.direction())) {
			return credit(
					lineId,
					entryId,
					originalLine.accountId(),
					originalLine.amountMinor(),
					originalLine.currency(),
					lineMemo,
					createdAt
			);
		}
		if ("CREDIT".equals(originalLine.direction())) {
			return debit(
					lineId,
					entryId,
					originalLine.accountId(),
					originalLine.amountMinor(),
					originalLine.currency(),
					lineMemo,
					createdAt
			);
		}
		throw new IllegalArgumentException("original line direction must be DEBIT or CREDIT");
	}

	private static LedgerEntryLine create(
			long lineId,
			long entryId,
			long accountId,
			String direction,
			long amountMinor,
			long signedAmountMinor,
			String currency,
			String lineMemo,
			long createdAt
	) {
		if (lineId <= 0) {
			throw new IllegalArgumentException("line_id must be positive");
		}
		if (entryId <= 0) {
			throw new IllegalArgumentException("entry_id must be positive");
		}
		if (accountId <= 0) {
			throw new IllegalArgumentException("account_id must be positive");
		}
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amount_minor must be positive");
		}
		if (currency == null || currency.length() != 3) {
			throw new IllegalArgumentException("currency must be a 3-letter code");
		}
		if (lineMemo == null || lineMemo.isBlank()) {
			throw new IllegalArgumentException("line_memo is required");
		}
		if (createdAt <= 0) {
			throw new IllegalArgumentException("created_at must be positive");
		}

		return new LedgerEntryLine(
				lineId,
				entryId,
				accountId,
				direction,
				amountMinor,
				signedAmountMinor,
				currency,
				lineMemo,
				createdAt
		);
	}
}
