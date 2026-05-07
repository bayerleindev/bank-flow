package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.Transfer;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferResponse(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("source_digital_account_id") String sourceDigitalAccountId,
		@JsonProperty("source_account") String sourceAccount,
		@JsonProperty("destination_digital_account_id") String destinationDigitalAccountId,
		@JsonProperty("destination_account") String destinationAccount,
		@JsonProperty("amount_minor") long amountMinor,
		String currency,
		String description,
		@JsonProperty("hold_id") String holdId,
		@JsonProperty("psp_payment_id") String pspPaymentId,
		String status,
		@JsonProperty("failure_reason") String failureReason,
		@JsonProperty("created_at") long createdAt,
		@JsonProperty("updated_at") long updatedAt
) {
	public static TransferResponse from(Transfer transfer) {
		return new TransferResponse(
				transfer.transferId().toString(),
				transfer.sourceDigitalAccountId().toString(),
				transfer.sourceAccount(),
				transfer.destinationDigitalAccountId().toString(),
				transfer.destinationAccount(),
				transfer.amountMinor(),
				transfer.currency(),
				transfer.description(),
				transfer.holdId(),
				transfer.pspPaymentId(),
				transfer.status().name(),
				transfer.failureReason(),
				transfer.createdAt(),
				transfer.updatedAt()
		);
	}
}
