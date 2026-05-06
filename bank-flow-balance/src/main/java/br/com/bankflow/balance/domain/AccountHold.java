package br.com.bankflow.balance.domain;

public record AccountHold(
		String holdId,
		String transferId,
		long accountId,
		long amountMinor,
		String currency,
		AccountHoldStatus status,
		String reason,
		long expiresAt,
		long createdAt,
		long updatedAt
) {
}
