package br.com.bankflow.transfer.services;

import br.com.bankflow.transfer.clients.accounts.AccountClient;
import br.com.bankflow.transfer.clients.accounts.AccountResponse;
import br.com.bankflow.transfer.clients.balance.BalanceClient;
import br.com.bankflow.transfer.clients.balance.BalanceHoldResponse;
import br.com.bankflow.transfer.clients.balance.CreateBalanceHoldRequest;
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
import br.com.bankflow.transfer.domain.TransferStatus;
import br.com.bankflow.transfer.observability.TransferBusinessMetrics;
import br.com.bankflow.transfer.repositories.OutboxEventRepository;
import br.com.bankflow.transfer.repositories.TransferRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TransferOrchestrationServiceTests {
	private static final UUID EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID = UUID.fromString("00000000-0000-0000-0000-000000000100");
	private final Clock clock = Clock.fixed(Instant.ofEpochMilli(1_778_100_000_000L), ZoneOffset.UTC);

	private final FakeTransferRepository repository = new FakeTransferRepository();
	private final FakeOutboxEventRepository outboxRepository = new FakeOutboxEventRepository();
	private final FakeAccountClient accountClient = new FakeAccountClient();
	private final FakeBalanceClient balanceClient = new FakeBalanceClient();
	private final FakePspClient pspClient = new FakePspClient();
	private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
	private final TransferBusinessMetrics transferBusinessMetrics = new TransferBusinessMetrics(meterRegistry, outboxRepository, repository, clock);
	private final TransferOrchestrationService service = new TransferOrchestrationService(
			repository,
			outboxRepository,
			accountClient,
			balanceClient,
			pspClient,
			new ObjectMapper(),
			transferBusinessMetrics,
			clock,
			"ledger-movements"
	);

	@Test
	void createsTransferWithHoldAndPendingPspPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		assertEquals(TransferStatus.PSP_PENDING, transfer.status());
		assertEquals("hold-" + transfer.transferId(), transfer.holdId());
		assertEquals("psp-" + transfer.transferId(), transfer.pspPaymentId());
		assertEquals(1, balanceClient.createdHolds);
		assertEquals(0, balanceClient.releasedHolds);
		assertEquals(1.0, meterRegistry.find("transfers_in_status").tag("status", "PSP_PENDING").gauge().value());
	}

	@Test
	void returnsExistingTransferForSameIdempotencyKey() {
		Transfer first = service.createTransfer(command("idem-1"));
		Transfer duplicate = service.createTransfer(command("idem-1"));

		assertEquals(first.transferId(), duplicate.transferId());
		assertEquals(1, balanceClient.createdHolds);
	}

	@Test
	void enqueuesLedgerPostingWhenWebhookConfirmsPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		Transfer confirmed = service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.CONFIRMED,
				null
		));

		assertEquals(TransferStatus.POSTING_REQUESTED, confirmed.status());
		assertEquals(1, outboxRepository.events.size());
		assertEquals("ledger-movements", outboxRepository.events.getFirst().topic());
		assertEquals(transfer.sourceDigitalAccountId().toString(), outboxRepository.events.getFirst().eventKey());
		assertEquals(0, balanceClient.releasedHolds);
	}

	@Test
	void capturesHoldAndCompletesTransferWhenLedgerPostingIsCreated() {
		Transfer transfer = service.createTransfer(command("idem-1"));
		service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.CONFIRMED,
				null
		));

		Transfer completed = service.completeAfterLedgerPosting(new LedgerPostingCreatedEvent(
				1001L,
				transfer.transferId().toString(),
				"TRANSFER",
				"POSTED"
		));

		assertEquals(TransferStatus.COMPLETED, completed.status());
		assertEquals(1, balanceClient.capturedHolds);
		assertEquals(0, balanceClient.releasedHolds);
		Timer timer = meterRegistry.find("transfer_end_to_end_latency").timer();
		assertNotNull(timer);
		assertEquals(1, timer.count());
	}

	@Test
	void releasesHoldAndFailsTransferWhenWebhookFailsPayment() {
		Transfer transfer = service.createTransfer(command("idem-1"));

		Transfer failed = service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.FAILED,
				"PSP_REJECTED"
		));

		assertEquals(TransferStatus.FAILED, failed.status());
		assertEquals("PSP_REJECTED", failed.failureReason());
		assertEquals(1, balanceClient.releasedHolds);
	}

	@Test
	void ignoresPspWebhookForExpiredTransfer() {
		Transfer transfer = service.createTransfer(command("idem-1"));
		repository.updateStatus(transfer.transferId(), TransferStatus.EXPIRED, "HOLD_EXPIRED", clock.millis());

		Transfer expired = service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.CONFIRMED,
				null
		));

		assertEquals(TransferStatus.EXPIRED, expired.status());
		assertEquals(0, outboxRepository.events.size());
		assertEquals(0, balanceClient.capturedHolds);
	}

	@Test
	void doesNotCompleteReversedTransferWhenLedgerEventArrivesLate() {
		Transfer transfer = service.createTransfer(command("idem-1"));
		service.handlePspWebhook(new PspWebhookCommand(
				transfer.pspPaymentId(),
				PspWebhookStatus.CONFIRMED,
				null
		));
		repository.updateStatus(transfer.transferId(), TransferStatus.REVERSED, "MANUAL_REVERSAL", clock.millis());

		Transfer reversed = service.completeAfterLedgerPosting(new LedgerPostingCreatedEvent(
				1001L,
				transfer.transferId().toString(),
				"TRANSFER",
				"POSTED"
		));

		assertEquals(TransferStatus.REVERSED, reversed.status());
		assertEquals(0, balanceClient.capturedHolds);
	}

	@Test
	void receivesExternalInboundTransferAndRequestsLedgerPostingWithoutHold() {
		Transfer transfer = service.receiveExternalInboundTransfer(new ExternalInboundTransferCommand(
				"external-inbound:260:evt-123",
				"260",
				"External Bank",
				"evt-123",
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b2"),
				2_500L,
				"BRL",
				"PIX recebido"
		));

		assertEquals(TransferStatus.POSTING_REQUESTED, transfer.status());
		assertEquals(EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID, transfer.sourceDigitalAccountId());
		assertEquals("SETTLEMENT_EXTERNAL_INBOUND_BRL", transfer.sourceAccount());
		assertEquals(0, balanceClient.createdHolds);
		assertEquals(1, outboxRepository.events.size());
		assertEquals(EXTERNAL_INBOUND_SETTLEMENT_DIGITAL_ACCOUNT_ID.toString(), outboxRepository.events.getFirst().eventKey());
	}

	@Test
	void returnsExistingExternalInboundTransferForSameSourceEvent() {
		ExternalInboundTransferCommand command = new ExternalInboundTransferCommand(
				"external-inbound:260:evt-123",
				"260",
				"External Bank",
				"evt-123",
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b2"),
				2_500L,
				"BRL",
				"PIX recebido"
		);

		Transfer first = service.receiveExternalInboundTransfer(command);
		Transfer duplicate = service.receiveExternalInboundTransfer(command);

		assertEquals(first.transferId(), duplicate.transferId());
		assertEquals(1, outboxRepository.events.size());
	}

	private CreateTransferCommand command(String idempotencyKey) {
		return new CreateTransferCommand(
				idempotencyKey,
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b1"),
				UUID.fromString("018f6e4f-f427-7c32-9d4b-3bc9e72872b2"),
				1500L,
				"BRL",
				"Test transfer"
		);
	}

	private static class FakeBalanceClient implements BalanceClient {
		private int createdHolds;
		private int capturedHolds;
		private int releasedHolds;

		@Override
		public BalanceHoldResponse createHold(CreateBalanceHoldRequest request) {
			createdHolds++;
			return new BalanceHoldResponse(
					"hold-" + request.transferId(),
					request.transferId(),
					request.digitalAccountId(),
					request.amountMinor(),
					request.currency(),
					"HELD",
					request.reason(),
					request.expiresAt(),
					1L,
					1L
			);
		}

		@Override
		public void captureHold(String holdId) {
			capturedHolds++;
		}

		@Override
		public void releaseHold(String holdId) {
			releasedHolds++;
		}
	}

	private static class FakePspClient implements PspClient {
		@Override
		public PspPaymentResponse createPayment(Transfer transfer) {
			return new PspPaymentResponse(
					"psp-" + transfer.transferId(),
					PspPaymentStatus.PENDING,
					null
			);
		}
	}

	private static class FakeTransferRepository implements TransferRepository {
		private final Map<UUID, Transfer> byId = new HashMap<>();
		private final Map<String, UUID> byIdempotencyKey = new HashMap<>();
		private final Map<String, UUID> byPspPaymentId = new HashMap<>();

		@Override
		public Optional<Transfer> findByIdempotencyKey(String idempotencyKey) {
			return Optional.ofNullable(byIdempotencyKey.get(idempotencyKey)).map(byId::get);
		}

		@Override
		public Optional<Transfer> findByTransferId(UUID transferId) {
			return Optional.ofNullable(byId.get(transferId));
		}

		@Override
		public Optional<Transfer> findByPspPaymentId(String pspPaymentId) {
			return Optional.ofNullable(byPspPaymentId.get(pspPaymentId)).map(byId::get);
		}

		@Override
		public long countByStatus(TransferStatus status) {
			return byId.values().stream()
					.filter(transfer -> transfer.status() == status)
					.count();
		}

		@Override
		public OptionalLong oldestUpdatedAtByStatus(TransferStatus status) {
			return byId.values().stream()
					.filter(transfer -> transfer.status() == status)
					.mapToLong(Transfer::updatedAt)
					.min();
		}

		@Override
		public Transfer create(UUID transferId, CreateTransferCommand command, String sourceAccount, String destinationAccount, long now) {
			Transfer transfer = new Transfer(
					transferId,
					command.idempotencyKey(),
					command.sourceDigitalAccountId(),
					sourceAccount,
					command.destinationDigitalAccountId(),
					destinationAccount,
					command.amountMinor(),
					command.currency(),
					command.description(),
					null,
					null,
					TransferStatus.RECEIVED,
					null,
					now,
					now
			);
			byId.put(transferId, transfer);
			byIdempotencyKey.put(command.idempotencyKey(), transferId);
			return transfer;
		}

		@Override
		public Transfer updateHold(UUID transferId, String holdId, TransferStatus status, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, holdId, current.pspPaymentId(), status, current.failureReason(), now);
			byId.put(transferId, updated);
			return updated;
		}

		@Override
		public Transfer updatePspPayment(UUID transferId, String pspPaymentId, TransferStatus status, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, current.holdId(), pspPaymentId, status, current.failureReason(), now);
			byId.put(transferId, updated);
			byPspPaymentId.put(pspPaymentId, transferId);
			return updated;
		}

		@Override
		public Transfer updateStatus(UUID transferId, TransferStatus status, String failureReason, long now) {
			Transfer current = byId.get(transferId);
			Transfer updated = copy(current, current.holdId(), current.pspPaymentId(), status, failureReason, now);
			byId.put(transferId, updated);
			return updated;
		}

		private Transfer copy(
				Transfer current,
				String holdId,
				String pspPaymentId,
				TransferStatus status,
				String failureReason,
				long updatedAt
		) {
			return new Transfer(
					current.transferId(),
					current.idempotencyKey(),
					current.sourceDigitalAccountId(),
					current.sourceAccount(),
					current.destinationDigitalAccountId(),
					current.destinationAccount(),
					current.amountMinor(),
					current.currency(),
					current.description(),
					holdId,
					pspPaymentId,
					status,
					failureReason,
					current.createdAt(),
					updatedAt
			);
		}
	}

	private static class FakeAccountClient implements AccountClient {
		@Override
		public AccountResponse getAccount(UUID digitalAccountId) {
			String account = digitalAccountId.toString().endsWith("2") ? "98765-4" : "12345-6";
			return new AccountResponse(digitalAccountId, "0001", account, "BRL", "ACTIVE");
		}
	}

	private static class FakeOutboxEventRepository implements OutboxEventRepository {
		private final java.util.List<OutboxEvent> events = new java.util.ArrayList<>();

		@Override
		public void createIfAbsent(OutboxEvent event) {
			boolean exists = events.stream()
					.anyMatch(existing -> existing.aggregateType().equals(event.aggregateType())
							&& existing.aggregateId().equals(event.aggregateId())
							&& existing.eventType().equals(event.eventType()));
			if (!exists) {
				events.add(event);
			}
		}

		@Override
		public java.util.List<OutboxEvent> findPending(int limit) {
			return events.stream()
					.filter(event -> "PENDING".equals(event.status()))
					.limit(limit)
					.toList();
		}

		@Override
		public void markPublished(UUID eventId, long publishedAt) {
		}

		@Override
		public void markFailed(UUID eventId, String errorMessage) {
		}
	}
}
