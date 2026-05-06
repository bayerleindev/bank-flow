package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.clients.balance.BalanceClient;
import br.com.bankflow.transfer.clients.balance.BalanceHoldResponse;
import br.com.bankflow.transfer.clients.balance.CreateBalanceHoldRequest;
import br.com.bankflow.transfer.clients.psp.PspClient;
import br.com.bankflow.transfer.clients.psp.PspPaymentResponse;
import br.com.bankflow.transfer.clients.psp.PspPaymentStatus;
import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.PspWebhookCommand;
import br.com.bankflow.transfer.domain.PspWebhookStatus;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.repositories.TransferRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class TransferOrchestrationService {
	private static final Logger log = LoggerFactory.getLogger(TransferOrchestrationService.class);
	private static final Duration HOLD_TTL = Duration.ofMinutes(15);

	private final TransferRepository transferRepository;
	private final BalanceClient balanceClient;
	private final PspClient pspClient;
	private final Clock clock;

	public TransferOrchestrationService(
			TransferRepository transferRepository,
			BalanceClient balanceClient,
			PspClient pspClient,
			Clock clock
	) {
		this.transferRepository = transferRepository;
		this.balanceClient = balanceClient;
		this.pspClient = pspClient;
		this.clock = clock;
	}

	public Transfer createTransfer(CreateTransferCommand command) {
		command.validate();
		return transferRepository.findByIdempotencyKey(command.idempotencyKey())
				.orElseGet(() -> orchestrateNewTransfer(command));
	}

	@Transactional
	public Transfer handlePspWebhook(PspWebhookCommand command) {
		command.validate();
		Transfer transfer = transferRepository.findByPspPaymentId(command.pspPaymentId())
				.orElseThrow(() -> new TransferNotFoundException(command.pspPaymentId()));
		if (transfer.status() == TransferStatus.COMPLETED || transfer.status() == TransferStatus.FAILED) {
			return transfer;
		}
		if (command.status() == PspWebhookStatus.FAILED) {
			releaseHoldIfPresent(transfer);
			return transferRepository.updateStatus(
					transfer.transferId(),
					TransferStatus.FAILED,
					command.failureReason() == null ? "PSP_FAILED" : command.failureReason(),
					clock.millis()
			);
		}
		log.info("psp payment confirmed transferId={} pspPaymentId={}",
				transfer.transferId(),
				transfer.pspPaymentId()
		);
		return transferRepository.updateStatus(
				transfer.transferId(),
				TransferStatus.PSP_CONFIRMED,
				null,
				clock.millis()
		);
	}

	public Transfer getTransfer(UUID transferId) {
		return transferRepository.findByTransferId(transferId)
				.orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
	}

	private Transfer orchestrateNewTransfer(CreateTransferCommand command) {
		Transfer transfer = createReceivedTransfer(command);
		try {
			BalanceHoldResponse hold = balanceClient.createHold(new CreateBalanceHoldRequest(
					transfer.transferId().toString(),
					command.sourceAccountId(),
					command.amountMinor(),
					command.currency(),
					"TRANSFER",
					clock.millis() + HOLD_TTL.toMillis()
			));
			transfer = updateHold(transfer.transferId(), hold.holdId());

			PspPaymentResponse payment = pspClient.createPayment(transfer);
			if (payment.status() == PspPaymentStatus.FAILED) {
				releaseHoldIfPresent(transfer);
				return updateFailed(transfer.transferId(), payment.failureReason() == null ? "PSP_FAILED" : payment.failureReason());
			}
			return updatePspPayment(
					transfer.transferId(),
					payment.pspPaymentId(),
					payment.status() == PspPaymentStatus.CONFIRMED
							? TransferStatus.PSP_CONFIRMED
							: TransferStatus.PSP_PENDING
			);
		} catch (RuntimeException exception) {
			releaseHoldIfPresent(transfer);
			updateFailed(transfer.transferId(), exception.getClass().getSimpleName());
			throw exception;
		}
	}

	@Transactional
	protected Transfer createReceivedTransfer(CreateTransferCommand command) {
		return transferRepository.create(UUID.randomUUID(), command, clock.millis());
	}

	@Transactional
	protected Transfer updateHold(UUID transferId, String holdId) {
		return transferRepository.updateHold(transferId, holdId, TransferStatus.HOLD_CREATED, clock.millis());
	}

	@Transactional
	protected Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status) {
		return transferRepository.updatePspPayment(transferId, pspPaymentId, status, clock.millis());
	}

	@Transactional
	protected Transfer updateFailed(UUID transferId, String failureReason) {
		return transferRepository.updateStatus(transferId, TransferStatus.FAILED, failureReason, clock.millis());
	}

	private void releaseHoldIfPresent(Transfer transfer) {
		if (transfer.holdId() == null || transfer.holdId().isBlank()) {
			return;
		}
		try {
			balanceClient.releaseHold(transfer.holdId());
		} catch (RuntimeException exception) {
			log.warn("failed to release hold transferId={} holdId={}",
					transfer.transferId(),
					transfer.holdId(),
					exception
			);
		}
	}
}
