package br.com.bankflow.balance.domain;

public record AccountStatementLine(
		long lineId,
		long entryId,
		java.util.UUID digitalAccountId,
		String externalId,
		String entryType,
		String direction,
		long amountMinor,
		long signedAmountMinor,
		String currency,
		String description,
		long occurredAt,
		long createdAt
) {
}
