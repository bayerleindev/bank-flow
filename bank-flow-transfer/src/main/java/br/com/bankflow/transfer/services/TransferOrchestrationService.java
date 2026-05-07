package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.clients.balance.BalanceClient;
import br.com.bankflow.transfer.clients.balance.BalanceHoldResponse;
import br.com.bankflow.transfer.clients.balance.CreateBalanceHoldRequest;
import br.com.bankflow.transfer.clients.accounts.AccountClient;
import br.com.bankflow.transfer.clients.accounts.AccountResponse;
import br.com.bankflow.transfer.clients.psp.PspClient;
import br.com.bankflow.transfer.clients.psp.PspPaymentResponse;
import br.com.bankflow.transfer.clients.psp.PspPaymentStatus;
import br.com.bankflow.transfer.domain.CreateTransferCommand;
import br.com.bankflow.transfer.domain.ExternalInboundTransferCommand;
import br.com.bankflow.transfer.domain.LedgerPostingCreatedEvent;
import br.com.bankflow.transfer.domain.OutboxEvent;
import br.com.bankflow.transfer.domain.PspWebhookCommand;
import br.com.bankflow.transfer.domain.PspWebhookStatus;
import br.com.bankflow.transfer.domain.Transfer;
import br.com.bankflow.transfer.domain.TransferPostedCommand;
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.observability.TransferBusinessMetrics;
import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import br.com.bankflow.transfer.repositories.TransferRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
	private static final UUID EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
	private static final String EXTERNAL_INBOUND_SETTLEMENT_ACCOUNT = "SETTLEMENT_EXTERNAL_INBOUND_BRL";

	private final TransferRepository transferRepository;
	private final OutboxEventRepository outboxEventRepository;
	private final AccountClient accountClient;
	private final BalanceClient balanceClient;
	private final PspClient pspClient;
	private final ObjectMapper objectMapper;
	private final TransferBusinessMetrics transferBusinessMetrics;
	private final Clock clock;
	private final String ledgerMovementsTopic;

	public TransferOrchestrationService(
			TransferRepository transferRepository,
			OutboxEventRepository outboxEventRepository,
			AccountClient accountClient,
			BalanceClient balanceClient,
			PspClient pspClient,
			ObjectMapper objectMapper,
			Clock clock,
			String ledgerMovementsTopic
	) {
		this(
				transferRepository,
				outboxEventRepository,
				accountClient,
				balanceClient,
				pspClient,
				objectMapper,
				new TransferBusinessMetrics(new SimpleMeterRegistry(), outboxEventRepository, transferRepository, clock),
				clock,
				ledgerMovementsTopic
		);
	}

	@Autowired
	public TransferOrchestrationService(
			TransferRepository transferRepository,
			OutboxEventRepository outboxEventRepository,
			AccountClient accountClient,
			BalanceClient balanceClient,
			PspClient pspClient,
			ObjectMapper objectMapper,
			TransferBusinessMetrics transferBusinessMetrics,
			Clock clock,
			@Value("${bank-flow.kafka.topics.ledger-movements}") String ledgerMovementsTopic
	) {
		this.transferRepository = transferRepository;
		this.outboxEventRepository = outboxEventRepository;
		this.accountClient = accountClient;
		this.balanceClient = balanceClient;
		this.pspClient = pspClient;
		this.objectMapper = objectMapper;
		this.transferBusinessMetrics = transferBusinessMetrics;
		this.clock = clock;
		this.ledgerMovementsTopic = ledgerMovementsTopic;
	}

	public Transfer createTransfer(CreateTransferCommand command) {
		command.validate();
		return transferRepository.findByIdempotencyKey(command.idempotencyKey())
				.orElseGet(() -> orchestrateNewTransfer(command));
	}

	public Transfer receiveExternalInboundTransfer(ExternalInboundTransferCommand command) {
		command.validate();
		return transferRepository.findByIdempotencyKey(command.idempotencyKey())
				.orElseGet(() -> requestLedgerPosting(createExternalInboundTransfer(command)));
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
			Transfer failed = transferRepository.updateStatus(
					transfer.transferId(),
					TransferStatus.FAILED,
					command.failureReason() == null ? "PSP_FAILED" : command.failureReason(),
					clock.millis()
			);
			transferBusinessMetrics.recordTransferFailed("psp_webhook");
			return failed;
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
		transferBusinessMetrics.recordTransferPspConfirmed();
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
		Transfer completed = transferRepository.updateStatus(
				transfer.transferId(),
				TransferStatus.COMPLETED,
				null,
				clock.millis()
		);
		transferBusinessMetrics.recordTransferCompleted();
		transferBusinessMetrics.recordTransferEndToEndLatency(completed.updatedAt() - completed.createdAt());
		return completed;
	}

	private Transfer orchestrateNewTransfer(CreateTransferCommand command) {
		AccountResponse sourceAccount = accountClient.getAccount(command.sourceDigitalAccountId());
		sourceAccount.validateActive("source");
		AccountResponse destinationAccount = accountClient.getAccount(command.destinationDigitalAccountId());
		destinationAccount.validateActive("destination");
		Transfer transfer = createReceivedTransfer(command, sourceAccount.account(), destinationAccount.account());
		try {
			BalanceHoldResponse hold = balanceClient.createHold(new CreateBalanceHoldRequest(
					transfer.transferId().toString(),
					command.sourceDigitalAccountId(),
					command.amountMinor(),
					command.currency(),
					"TRANSFER",
					clock.millis() + HOLD_TTL.toMillis()
			));
			transfer = updateHold(transfer.transferId(), hold.holdId());

			PspPaymentResponse payment = pspClient.createPayment(transfer);
			if (payment.status() == PspPaymentStatus.FAILED) {
				releaseHoldIfPresent(transfer);
				Transfer failed = updateFailed(transfer.transferId(), payment.failureReason() == null ? "PSP_FAILED" : payment.failureReason());
				transferBusinessMetrics.recordTransferFailed("psp_create_payment");
				return failed;
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
			transferBusinessMetrics.recordTransferFailed(exception.getClass().getSimpleName());
			throw exception;
		}
	}

	@Transactional
	protected Transfer createReceivedTransfer(CreateTransferCommand command, String sourceAccount, String destinationAccount) {
		Transfer transfer = transferRepository.create(UUID.randomUUID(), command, sourceAccount, destinationAccount, clock.millis());
		transferBusinessMetrics.recordTransferCreated();
		return transfer;
	}

	@Transactional
	protected Transfer createExternalInboundTransfer(ExternalInboundTransferCommand command) {
		AccountResponse destinationAccount = accountClient.getAccount(command.destinationDigitalAccountId());
		destinationAccount.validateActive("destination");
		CreateTransferCommand transferCommand = new CreateTransferCommand(
				command.idempotencyKey(),
				EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID,
				command.destinationDigitalAccountId(),
				command.amountMinor(),
				command.currency(),
				command.description()
		);
		return createReceivedTransfer(transferCommand, EXTERNAL_INBOUND_SETTLEMENT_ACCOUNT, destinationAccount.account());
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
					transfer.sourceDigitalAccountId().toString(),
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
