package br.com.bankflow.transfer.domain;

public record CreateTransferCommand(
		String idempotencyKey,
		java.util.UUID sourceDigitalAccountId,
		java.util.UUID destinationDigitalAccountId,
		long amountMinor,
		String currency,
		String description
) {
	public void validate() {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			throw new IllegalArgumentException("Idempotency-Key is required");
		}
		if (sourceDigitalAccountId == null) {
			throw new IllegalArgumentException("source_digital_account_id is required");
		}
		if (destinationDigitalAccountId == null) {
			throw new IllegalArgumentException("destination_digital_account_id is required");
		}
		if (sourceDigitalAccountId.equals(destinationDigitalAccountId)) {
			throw new IllegalArgumentException("source_digital_account_id and destination_digital_account_id must be different");
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
