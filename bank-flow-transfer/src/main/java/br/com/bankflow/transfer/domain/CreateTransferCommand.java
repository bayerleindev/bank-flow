package br.com.bankflow.transfer.domain;

public record CreateTransferCommand(
		String idempotencyKey,
		long sourceAccountId,
		java.util.UUID sourceOwnerId,
		String sourceAccount,
		long destinationAccountId,
		java.util.UUID destinationOwnerId,
		String destinationAccount,
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
		if (sourceOwnerId == null) {
			throw new IllegalArgumentException("source_owner_id is required");
		}
		if (sourceAccount == null || sourceAccount.isBlank()) {
			throw new IllegalArgumentException("source_account is required");
		}
		if (destinationAccountId <= 0) {
			throw new IllegalArgumentException("destination_account_id must be positive");
		}
		if (destinationOwnerId == null) {
			throw new IllegalArgumentException("destination_owner_id is required");
		}
		if (destinationAccount == null || destinationAccount.isBlank()) {
			throw new IllegalArgumentException("destination_account is required");
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
