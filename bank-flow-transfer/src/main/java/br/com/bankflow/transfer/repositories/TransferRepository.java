package br.com.bankflow.transfer.repositories;

import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;

import java.util.Optional;
import java.util.UUID;

public interface TransferRepository {
	Optional<Transfer> findByIdempotencyKey(String idempotencyKey);

	Optional<Transfer> findByTransferId(UUID transferId);

	Optional<Transfer> findByPspPaymentId(String pspPaymentId);

	Transfer create(UUID transferId, CreateTransferCommand command, long now);

	Transfer updateHold(UUID transferId, String holdId, TransferStatus status, long now);

	Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status, long now);

	Transfer updateStatus(UUID transferId, TransferStatus status, String failureReason, long now);
}
