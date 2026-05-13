package br.com.bankflow.ledger.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TransferPostedEvent(
		@JsonProperty("transfer_id") UUID transferId,
		@JsonProperty("source_digital_account_id") UUID sourceDigitalAccountId,
		@JsonProperty("source_account") String sourceAccount,
		@JsonProperty("destination_digital_account_id") UUID destinationDigitalAccountId,
		@JsonProperty("destination_account") String destinationAccount,
		@JsonProperty("amount_cents") long amountCents,
		String currency,
		@JsonProperty("transfer_created_at") long transferCreatedAt
) {
	public TransferPostedEvent(
			UUID transferId,
			UUID sourceDigitalAccountId,
			String sourceAccount,
			UUID destinationDigitalAccountId,
			String destinationAccount,
			long amountCents,
			String currency
	) {
		this(
				transferId,
				sourceDigitalAccountId,
				sourceAccount,
				destinationDigitalAccountId,
				destinationAccount,
				amountCents,
				currency,
				0
		);
	}

	public void validate() {
		if (transferId == null) {
			throw new IllegalArgumentException("transfer_id is required");
		}
		if (sourceDigitalAccountId == null) {
			throw new IllegalArgumentException("source_digital_account_id is required");
		}
		if (isBlank(sourceAccount)) {
			throw new IllegalArgumentException("source_account is required");
		}
		if (destinationDigitalAccountId == null) {
			throw new IllegalArgumentException("destination_digital_account_id is required");
		}
		if (isBlank(destinationAccount)) {
			throw new IllegalArgumentException("destination_account is required");
		}
		if (amountCents <= 0) {
			throw new IllegalArgumentException("amount_cents must be positive");
		}
		if (!"BRL".equals(currency)) {
			throw new IllegalArgumentException("currency must be BRL");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
