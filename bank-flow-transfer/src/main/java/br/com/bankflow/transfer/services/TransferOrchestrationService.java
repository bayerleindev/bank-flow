package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.clients.balance.BalanceClient;
import br.com.bankflow.transfer.clients.balance.BalanceHoldResponse;
import br.com.bankflow.transfer.clients.balance.CreateBalanceHoldRequest;
import br.com.bankflow.transfer.clients.psp.PspClient;
import br.com.bankflow.transfer.clients.psp.PspPaymentResponse;
import br.com.bankflow.transfer.clients.psp.PspPaymentStatus;
import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.domain.OutboxEvent;
import br.com.bankflow.transfer.domain.PspWebhookCommand;
import br.com.bankflow.transfer.domain.PspWebhookStatus;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferPostedCommand;
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import br.com.bankflow.transfer.repositories.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

@Service
public class TransferOrchestrationService {
	private static final Logger log = LoggerFactory.getLogger(TransferOrchestrationService.class);
	private static final Duration HOLD_TTL = Duration.ofMinutes(15);
	private static final String LEDGER_TRANSFER_POSTED_EVENT = "ledger.transfer_posted";

	private final TransferRepository transferRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final BalanceClient balanceClient;
	private final PspClient pspClient;
	private final ObjectMapper objectMapper;
	private final Clock clock;
	private final String ledgerMovementsTopic;

	public TransferOrchestrationService(
			TransferRepository transferRepository,
			OutboxEventRepository outboxEventRepository,
			BalanceClient balanceClient,
			PspClient pspClient,
			ObjectMapper objectMapper,
			Clock clock,
			@Value("${bank-flow.kafka.topics.ledger-movements}") String ledgerMovementsTopic
	) {
		this.transferRepository = transferRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.balanceClient = balanceClient;
		this.pspClient = pspClient;
		this.objectMapper = objectMapper;
		this.clock = clock;
		this.ledgerMovementsTopic = ledgerMovementsTopic;
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
		if (transfer.status() == TransferStatus.POSTING_REQUESTED) {
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
		Transfer confirmed = transferRepository.updateStatus(
				transfer.transferId(),
				TransferStatus.PSP_CONFIRMED,
				null,
				clock.millis()
		);
		return requestLedgerPosting(confirmed);
	}

	public Transfer getTransfer(UUID transferId) {
		return transferRepository.findByTransferId(transferId)
				.orElseThrow(() -> new TransferNotFoundException(transferId.toString()));
	}

	@Transactional
	public Transfer completeAfterLedgerPosting(LedgerPostingCreatedEvent event) {
		event.validate();
		UUID transferId = UUID.fromString(event.externalId());
		Transfer transfer = transferRepository.findByTransferId(transferId)
				.orElseThrow(() -> new TransferNotFoundException(event.externalId()));
		if (transfer.status() == TransferStatus.COMPLETED) {
			return transfer;
		}
		if (transfer.status() == TransferStatus.FAILED) {
			return transfer;
		}
		if (transfer.status() != TransferStatus.POSTING_REQUESTED) {
			throw new IllegalStateException("transfer must be POSTING_REQUESTED before completion");
		}
		captureHoldIfPresent(transfer);
		return transferRepository.updateStatus(
				transfer.transferId(),
				TransferStatus.COMPLETED,
				null,
				clock.millis()
		);
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

	private Transfer requestLedgerPosting(Transfer transfer) {
		try {
			long now = clock.millis();
			TransferPostedCommand command = TransferPostedCommand.from(transfer);
			outboxEventRepository.createIfAbsent(new OutboxEvent(
					UUID.randomUUID(),
					"Transfer",
					transfer.transferId().toString(),
					LEDGER_TRANSFER_POSTED_EVENT,
					ledgerMovementsTopic,
					transfer.sourceOwnerId().toString(),
					objectMapper.writeValueAsString(command),
					"PENDING",
					0,
					null,
					now,
					null
			));
			return transferRepository.updateStatus(transfer.transferId(), TransferStatus.POSTING_REQUESTED, null, now);
		} catch (JsonProcessingException exception) {
			throw new IllegalStateException("failed to serialize ledger transfer command", exception);
		}
	}

	private void captureHoldIfPresent(Transfer transfer) {
		if (transfer.holdId() == null || transfer.holdId().isBlank()) {
			return;
		}
		balanceClient.captureHold(transfer.holdId());
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
