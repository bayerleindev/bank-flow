package br.com.bankflow.transfer.domain;

import java.util.UUID;

public record Transfer(
		UUID transferId,
		String idempotencyKey,
		UUID sourceDigitalAccountId,
		String sourceAccount,
		UUID destinationDigitalAccountId,
		String destinationAccount,
		long amountMinor,
		String currency,
		String description,
		String holdId,
		String pspPaymentId,
		TransferStatus status,
		String failureReason,
		long createdAt,
		long updatedAt
) {
}
