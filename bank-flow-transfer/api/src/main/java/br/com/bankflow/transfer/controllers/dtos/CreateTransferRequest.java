package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransferRequest(
		@JsonProperty("source_digital_account_id") java.util.UUID sourceDigitalAccountId,
		@JsonProperty("destination_digital_account_id") java.util.UUID destinationDigitalAccountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String description
) {
	public CreateTransferCommand toCommand(String idempotencyKey) {
		return new CreateTransferCommand(
				idempotencyKey,
				sourceDigitalAccountId,
				destinationDigitalAccountId,
				amountMinor,
				currency,
				description
		);
	}
}
