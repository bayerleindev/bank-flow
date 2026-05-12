package br.com.bankflow.balance.domain;

public record AccountHold(
		String holdId,
		String transferId,
		java.util.UUID digitalAccountId,
		long amountMinor,
		String currency,
		AccountHoldStatus status,
		String reason,
		long expiresAt,
		long createdAt,
		long updatedAt
) {
}
