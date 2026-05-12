package br.com.bankflow.transfer.controllers.dtos;

import br.com.bankflow.transfer.domain.PspWebhookCommand;
import br.com.bankflow.transfer.domain.PspWebhookStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PspWebhookRequest(
		@JsonProperty("psp_payment_id") String pspPaymentId,
		String status,
		@JsonProperty("failure_reason") String failureReason
) {
	public PspWebhookCommand toCommand() {
		return new PspWebhookCommand(
				pspPaymentId,
				status == null ? null : PspWebhookStatus.valueOf(status),
				failureReason
		);
	}
}
