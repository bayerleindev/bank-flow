package br.com.bankflow.transfer.clients.psp;

public record PspPaymentResponse(
		String pspPaymentId,
		PspPaymentStatus status,
		String failureReason
) {
}
