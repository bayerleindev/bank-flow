package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

public interface TransferRepository {
	Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

	Optional<Transfer> findByTransferId(UUID transferId);

	Optional<Transfer> findByPspPaymentId(String pspPaymentId);

	long countByStatus(TransferStatus status);

	OptionalLong oldestUpdatedAtByStatus(TransferStatus status);

	Transfer create(UUID transferId, CreateTransferCommand command, String sourceAccount, String destinationAccount, long now);

	Transfer updateHold(UUID transferId, String holdId, TransferStatus status, long now);

	Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status, long now);

	Transfer updateStatus(UUID transferId, TransferStatus status, String failureReason, long now);
}
