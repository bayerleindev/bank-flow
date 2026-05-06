package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.Transfer;
import com.fasterxml.jackson.annotation.JsonProperty;

public record TransferResponse(
		@JsonProperty("transfer_id") String transferId,
		@JsonProperty("source_account_id") long sourceAccountId,
		@JsonProperty("destination_account_id") long destinationAccountId,
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
				transfer.sourceAccountId(),
				transfer.destinationAccountId(),
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
