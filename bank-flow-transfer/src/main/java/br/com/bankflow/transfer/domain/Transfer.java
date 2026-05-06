package br.com.bankflow.transfer.domain;

import java.util.UUID;

public record Transfer(
		UUID transferId,
		String idempotencyKey,
		long sourceAccountId,
		long destinationAccountId,
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
