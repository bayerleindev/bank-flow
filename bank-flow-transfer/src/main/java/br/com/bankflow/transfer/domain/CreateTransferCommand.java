package br.com.bankflow.transfer.domain;

public record CreateTransferCommand(
		String idempotencyKey,
		long sourceAccountId,
		long destinationAccountId,
		long amountMinor,
		String currency,
		String description
) {
	public void validate() {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("Idempotency-Key is required");
		}
		if (sourceAccountId <= 0) {
			throw new IllegalArgumentException("source_account_id must be positive");
		}
		if (destinationAccountId <= 0) {
			throw new IllegalArgumentException("destination_account_id must be positive");
		}
		if (sourceAccountId == destinationAccountId) {
			throw new IllegalArgumentException("source_account_id and destination_account_id must be different");
		}
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amount_minor must be positive");
		}
		if (currency == null || currency.length() != 3) {
			throw new IllegalArgumentException("currency must be a 3-letter code");
		}
		if (description == null || description.isBlank()) {
			throw new IllegalArgumentException("description is required");
		}
	}
}
