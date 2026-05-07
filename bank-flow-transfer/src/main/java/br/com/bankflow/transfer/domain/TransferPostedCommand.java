package br.com.bankflow.transfer.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

public record TransferPostedCommand(
		@JsonProperty("transfer_id") UUID transferId,
		@JsonProperty("source_digital_account_id") UUID sourceDigitalAccountId,
		@JsonProperty("source_account") String sourceAccount,
		@JsonProperty("destination_digital_account_id") UUID destinationDigitalAccountId,
		@JsonProperty("destination_account") String destinationAccount,
		@JsonProperty("amount_cents") long amountCents,
		String currency
) {
	public static TransferPostedCommand from(Transfer transfer) {
		return new TransferPostedCommand(
				transfer.transferId(),
				transfer.sourceDigitalAccountId(),
				transfer.sourceAccount(),
				transfer.destinationDigitalAccountId(),
				transfer.destinationAccount(),
				transfer.amountMinor(),
				transfer.currency()
		);
	}
}
