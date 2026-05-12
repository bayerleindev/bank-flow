package br.com.bankflow.transfer.domain;

public record PspWebhookCommand(
		String pspPaymentId,
		PspWebhookStatus status,
		String failureReason
) {
	public void validate() {
		if (pspPaymentId == null || pspPaymentId.isBlank()) {
			throw new IllegalArgumentException("psp_payment_id is required");
		}
		if (status == null) {
			throw new IllegalArgumentException("status is required");
		}
	}
}
