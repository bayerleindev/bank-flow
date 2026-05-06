package br.com.bankflow.balance.domain;

public record CreateAccountHoldCommand(
		String transferId,
		long accountId,
		long amountMinor,
		String currency,
		String reason,
		long expiresAt
) {
	public void validate(long now) {
		if (isBlank(transferId)) {
			throw new IllegalArgumentException("transfer_id is required");
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
		if (isBlank(reason)) {
			throw new IllegalArgumentException("reason is required");
		}
		if (expiresAt <= now) {
			throw new IllegalArgumentException("expires_at must be in the future");
		}
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
