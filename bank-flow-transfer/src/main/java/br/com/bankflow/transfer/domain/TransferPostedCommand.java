package br.com.bankflow.transfer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TransferPostedCommand(
		@JsonProperty("transfer_id") UUID transferId,
		@JsonProperty("source_owner_id") UUID sourceOwnerId,
		@JsonProperty("source_account") String sourceAccount,
		@JsonProperty("destination_owner_id") UUID destinationOwnerId,
		@JsonProperty("destination_account") String destinationAccount,
		@JsonProperty("amount_cents") long amountCents,
		String currency
) {
	public static TransferPostedCommand from(Transfer transfer) {
		return new TransferPostedCommand(
				transfer.transferId(),
				transfer.sourceOwnerId(),
				transfer.sourceAccount(),
				transfer.destinationOwnerId(),
				transfer.destinationAccount(),
				transfer.amountMinor(),
				transfer.currency()
		);
	}
}
