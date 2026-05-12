package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.ExternalInboundTransferCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record ExternalInboundTransferWebhookRequest(
		@JsonProperty("source_institution_code") String sourceInstitutionCode,
		@JsonProperty("source_institution_name") String sourceInstitutionName,
		@JsonProperty("external_transfer_id") String externalTransferId,
		@JsonProperty("destination_digital_account_id") UUID destinationDigitalAccountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String description
) {
	public ExternalInboundTransferCommand toCommand() {
		String idempotencyKey = "external-inbound:%s:%s".formatted(sourceInstitutionCode, externalTransferId);
		return new ExternalInboundTransferCommand(
				idempotencyKey,
				sourceInstitutionCode,
				sourceInstitutionName,
				externalTransferId,
				destinationDigitalAccountId,
				amountMinor,
				currency,
				description
		);
	}
}
