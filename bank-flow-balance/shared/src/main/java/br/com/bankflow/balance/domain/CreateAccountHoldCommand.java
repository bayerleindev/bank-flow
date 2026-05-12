package br.com.bankflow.balance.domain;

public record CreateAccountHoldCommand(
		String transferId,
		java.util.UUID digitalAccountId,
		long amountMinor,
		String currency,
		String reason,
		long expiresAt
) {
	public void validate(long now) {
		if (isBlank(transferId)) {
			throw new IllegalArgumentException("transfer_id is required");
		}
		if (digitalAccountId == null) {
			throw new IllegalArgumentException("digital_account_id is required");
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
