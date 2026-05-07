package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateTransferRequest(
		@JsonProperty("source_account_id") long sourceAccountId,
		@JsonProperty("source_owner_id") java.util.UUID sourceOwnerId,
		@JsonProperty("source_account") String sourceAccount,
		@JsonProperty("destination_account_id") long destinationAccountId,
		@JsonProperty("destination_owner_id") java.util.UUID destinationOwnerId,
		@JsonProperty("destination_account") String destinationAccount,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String description
) {
	public CreateTransferCommand toCommand(String idempotencyKey) {
		return new CreateTransferCommand(
				idempotencyKey,
				sourceAccountId,
				sourceOwnerId,
				sourceAccount,
				destinationAccountId,
				destinationOwnerId,
				destinationAccount,
				amountMinor,
				currency,
				description
		);
	}
}
