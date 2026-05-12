package br.com.bankflow.transfer.domain;

import java.util.UUID;

public record ExternalInboundTransferCommand(
		String idempotencyKey,
		String sourceInstitutionCode,
		String sourceInstitutionName,
		String externalTransferId,
		UUID destinationDigitalAccountId,
		long amountMinor,
		String currency,
		String description
) {
	public void validate() {
		if (isBlank(idempotencyKey)) {
			throw new IllegalArgumentException("idempotency_key is required");
		}
		if (isBlank(sourceInstitutionCode)) {
			throw new IllegalArgumentException("source_institution_code is required");
		}
		if (isBlank(externalTransferId)) {
			throw new IllegalArgumentException("external_transfer_id is required");
		}
		if (destinationDigitalAccountId == null) {
			throw new IllegalArgumentException("destination_digital_account_id is required");
		}
		if (amountMinor <= 0) {
			throw new IllegalArgumentException("amount_minor must be positive");
		}
		if (!"BRL".equals(currency)) {
			throw new IllegalArgumentException("currency must be BRL");
		}
		if (isBlank(description)) {
			throw new IllegalArgumentException("description is required");
		}
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
