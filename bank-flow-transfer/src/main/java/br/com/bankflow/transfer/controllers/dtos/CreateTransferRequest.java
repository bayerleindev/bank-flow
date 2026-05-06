package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransferRequest(
		@JsonProperty("source_account_id") long sourceAccountId,
		@JsonProperty("destination_account_id") long destinationAccountId,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String description
) {
	public CreateTransferCommand toCommand(String idempotencyKey) {
		return new CreateTransferCommand(
				idempotencyKey,
				sourceAccountId,
				destinationAccountId,
				amountMinor,
				currency,
				description
		);
	}
}
