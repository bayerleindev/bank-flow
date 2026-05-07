package br.com.bankflow.transfer.domain;

import java.util.UUID;

public record Transfer(
		UUID transferId,
		String idempotencyKey,
		long sourceAccountId,
		java.util.UUID sourceOwnerId,
		String sourceAccount,
		long destinationAccountId,
		java.util.UUID destinationOwnerId,
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
